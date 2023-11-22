package cp2023.solution;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;

import java.util.concurrent.Semaphore;
import java.util.concurrent.ConcurrentHashMap;

import cp2023.base.ComponentId;
import cp2023.base.DeviceId;
import cp2023.base.StorageSystem;
import cp2023.base.ComponentTransfer;

import cp2023.exceptions.*;

public class MyStorageSystem implements StorageSystem {
    private Map<DeviceId, Integer> deviceTotalSlots;                            // Copy from constructor

    // Up to date component placement, removed if component has left the system
    private Semaphore componentPlacementMutex = new Semaphore(1, true);
    private Map<ComponentId, DeviceId> componentPlacement;                      // Copy from constructor
    private Map<ComponentId, Boolean> isBeingTransfered                         = new ConcurrentHashMap<>();

    // A mutex for searching/modifying the graph (deviceFreeSlots and waitingForImport)
    private Semaphore GM = new Semaphore(1, true);
    private Map<DeviceId, Integer> deviceFreeSlots                              = new ConcurrentHashMap<>();
    private Map<DeviceId, LinkedList<ComponentTransfer> > waitingForImport      = new ConcurrentHashMap<>();

    // Transfer specific semaphores and a transfer that I have to wait for (next) / is waiting for me (prev)
    // If I am not waiting for any transfer / no transfer is waiting for me than next/prev[me] = me
    private Map<ComponentTransfer, Semaphore> waitForStart                      = new ConcurrentHashMap<>(); 
    private Map<ComponentTransfer, Semaphore> waitForNextTransfer               = new ConcurrentHashMap<>(); 
    private Map<ComponentTransfer, ComponentTransfer> prevTransfer              = new ConcurrentHashMap<>();
    private Map<ComponentTransfer, ComponentTransfer> nextTransfer              = new ConcurrentHashMap<>();

    public MyStorageSystem(Map<DeviceId, Integer> deviceTotalSlots, Map<ComponentId, DeviceId> componentPlacement) {
        // Component specific data
        this.componentPlacement = new ConcurrentHashMap(componentPlacement);
        for (ComponentId componentId : componentPlacement.keySet()) {
            isBeingTransfered.put(componentId, false);
        }
        
        // Graph data
        for (DeviceId deviceId : deviceTotalSlots.keySet()) {
            waitingForImport.put(deviceId, new LinkedList<>());
        }
    
        // Device specific data
        this.deviceTotalSlots = new ConcurrentHashMap(deviceTotalSlots);
        deviceFreeSlots = new ConcurrentHashMap(deviceTotalSlots); 
        for (Map.Entry<ComponentId, DeviceId> entry : componentPlacement.entrySet()) {
            deviceFreeSlots.put(entry.getValue(), deviceFreeSlots.get(entry.getValue()) - 1); 
        }
    }

    public void execute(ComponentTransfer transfer) throws TransferException {
        // Check if transfer is legal and mark component as being transfered
        acquire(componentPlacementMutex);
        checkIfTransferLegal(transfer); 
        isBeingTransfered.put(transfer.getComponentId(), true);
        componentPlacementMutex.release();

        // Create and initialize transfer specific values
        waitForStart.put(transfer, new Semaphore(1, true));
        waitForNextTransfer.put(transfer, new Semaphore(1, true));
        acquire(waitForStart.get(transfer));
        acquire(waitForNextTransfer.get(transfer));
        prevTransfer.put(transfer, transfer);
        nextTransfer.put(transfer, transfer);

        // If the transfer is a removal of a file, immidiately start
        if (transfer.getDestinationDeviceId() == null) { 
            start(transfer);
            return;
        }
 
        acquire(GM);
        // If there is a free slot on destination device, take it and start
        if (deviceFreeSlots.get(transfer.getDestinationDeviceId()) > 0) { 
            deviceFreeSlots.put(transfer.getDestinationDeviceId(), deviceTotalSlots.get(transfer.getDestinationDeviceId()) - 1);
            GM.release();
            start(transfer);
            return;
        }

        List<ComponentTransfer> cycle = getCycle(transfer);

        // If there is no cycle add myself to the graph and wait on my semaphore
        if (cycle == null) {
            waitingForImport.get(transfer.getDestinationDeviceId()).addLast(transfer);
            GM.release();
            acquire(waitForStart.get(transfer));
            start(transfer);
            return;
        }
        
        // Remove all of the cycle from the graph
        for (ComponentTransfer ct : cycle) {
            waitingForImport.get(ct.getDestinationDeviceId()).remove(ct);
        }
        GM.release();

        // Set the prevTransfer and nextTransfer for all transfers in cycle
        ComponentTransfer nextCT = transfer, currentCT = null;
        for (ComponentTransfer ct : cycle) {
            currentCT = ct;
            prevTransfer.put(nextCT, currentCT);
            nextTransfer.put(currentCT, nextCT);
        }
        prevTransfer.put(currentCT, transfer);
        nextTransfer.put(transfer, currentCT);

        // Release all transfers from the cycle
        for (ComponentTransfer ct : cycle) {
            waitForStart.get(ct).release();
        }

        start(transfer);

        // update component placement
        acquire(componentPlacementMutex);
        if (transfer.getDestinationDeviceId() == null) {
            componentPlacement.remove(transfer.getComponentId());
            isBeingTransfered.remove(transfer.getComponentId());
        }
        else {
            componentPlacement.put(transfer.getComponentId(), transfer.getDestinationDeviceId());
            isBeingTransfered.put(transfer.getComponentId(), false);
        }
        componentPlacementMutex.release();
    }

    private void checkIfTransferLegal(ComponentTransfer transfer) throws TransferException {
        // if source is null and destination is null
        if (transfer.getSourceDeviceId() == null && transfer.getDestinationDeviceId() == null){
            componentPlacementMutex.release();
            throw new IllegalTransferType(transfer.getComponentId());
        }
        // if the component exist and is being transfered
        if (componentPlacement.containsKey(transfer.getComponentId()) && isBeingTransfered.get(transfer.getComponentId())){
            componentPlacementMutex.release();
            throw new ComponentIsBeingOperatedOn(transfer.getComponentId());
        }
        // if the transfer is addition and the component exists
        if (transfer.getSourceDeviceId() == null && componentPlacement.containsKey(transfer.getComponentId())){
            DeviceId currentPlacement = componentPlacement.get(transfer.getComponentId());
            componentPlacementMutex.release();
            throw new ComponentAlreadyExists(transfer.getComponentId(), currentPlacement);
        }
        // if the transfer is not addition and (the component doesn't exist or is on a different device)
        if (transfer.getSourceDeviceId() != null && (!componentPlacement.containsKey(transfer.getComponentId()) || !componentPlacement.get(transfer.getComponentId()).equals(transfer.getSourceDeviceId()))){
            componentPlacementMutex.release();
            throw new ComponentDoesNotExist(transfer.getComponentId(), transfer.getSourceDeviceId());
        }
        // if the transfer is not removal and destination doesn't exist
        if (transfer.getDestinationDeviceId() != null && !deviceTotalSlots.containsKey(transfer.getDestinationDeviceId())){
            componentPlacementMutex.release();
            throw new DeviceDoesNotExist(transfer.getDestinationDeviceId());
        }
        // if the transfer is not addition and source doesn't exist
        if (transfer.getSourceDeviceId() != null && !deviceTotalSlots.containsKey(transfer.getSourceDeviceId())){
            componentPlacementMutex.release();
            throw new DeviceDoesNotExist(transfer.getSourceDeviceId());
        }
        // if the source is the destination
        if (transfer.getSourceDeviceId() == transfer.getDestinationDeviceId()){
            componentPlacementMutex.release();
            throw new ComponentDoesNotNeedTransfer(transfer.getComponentId(), transfer.getDestinationDeviceId());
        }
    }

    private void start(ComponentTransfer transfer) {
        acquire(GM);
        tryGetPrev(transfer);
        GM.release();

        transfer.prepare();

        acquire(GM);
        // If a new prev transfer has showed up while we were preparing, get it now
        tryGetPrev(transfer);

        // Release prevTransfer if they exist otherwise free a slot 
        if (prevTransfer.get(transfer) != transfer)
            waitForNextTransfer.get(prevTransfer.get(transfer)).release();
        else if (transfer.getSourceDeviceId() != null) {
            deviceFreeSlots.put(transfer.getSourceDeviceId(), deviceFreeSlots.get(transfer.getSourceDeviceId()) - 1);
        }
        GM.release();

        // If the next transfer exists, wait for it, than perform
        if (nextTransfer.get(transfer) != transfer) 
            acquire(waitForNextTransfer.get(transfer));
        transfer.perform();

        // Clean transfer specific values
        waitForStart.remove(transfer);
        waitForNextTransfer.remove(transfer);
        prevTransfer.remove(transfer);
        nextTransfer.remove(transfer);
    }

    // If the transfer is not an addition and there isn't a prevTransfer chosen yet, choose the oldest waiting, remove and release them
    private void tryGetPrev(ComponentTransfer transfer) {
        if (transfer.getSourceDeviceId() != null && prevTransfer.get(transfer) == transfer) {            
            if (!waitingForImport.get(transfer.getSourceDeviceId()).isEmpty()) {
                prevTransfer.put(transfer, waitingForImport.get(transfer.getSourceDeviceId()).getFirst());
                nextTransfer.put(waitingForImport.get(transfer.getSourceDeviceId()).getFirst(), transfer);
                waitingForImport.get(transfer.getSourceDeviceId()).removeFirst();
            }

            if (prevTransfer.get(transfer) != transfer)
                waitForStart.get(prevTransfer.get(transfer)).release();
        }
    }

    // If there is a cycle, return it in order from transfer.source to transfer.destination without myself, otherwise return null
    private List <ComponentTransfer> getCycle(ComponentTransfer transfer) {
        // If I am addition or removal, there is no cycle
        if (transfer.getSourceDeviceId() == null || transfer.getDestinationDeviceId() == null)
            return null;

        // Create visited map
        Map<DeviceId, Boolean> visited = new HashMap<>();
        for (DeviceId deviceId : deviceTotalSlots.keySet()) {
            visited.put(deviceId, false);
        }

        // Run reverse dfs from my source
        LinkedList <ComponentTransfer> res = new LinkedList<>();
        if (dfs(transfer.getSourceDeviceId(), transfer.getDestinationDeviceId(), visited, res))
            return res;
        return null;
    }

    private Boolean dfs(DeviceId current, DeviceId destination, Map<DeviceId, Boolean> visited, LinkedList<ComponentTransfer> res) {
        visited.put(current, true);

        for (ComponentTransfer edge : waitingForImport.get(current)) {
            if (edge.getSourceDeviceId() == null || visited.get(edge.getSourceDeviceId()))
                continue;
            res.addLast(edge);
            if (dfs(edge.getSourceDeviceId(), destination, visited, res))
                return true;
            res.removeLast();
        }

        return false;
    }

    private void acquire(Semaphore s) {
        try {
            s.acquire();
        } catch(InterruptedException e) {
            throw new RuntimeException("panic: unexpected thread interruption");
        }
    }
}