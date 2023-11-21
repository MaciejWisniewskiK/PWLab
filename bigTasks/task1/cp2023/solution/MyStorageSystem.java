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

    private Map<DeviceId, Integer> deviceTotalSlots;

    // Up to date component placement, removed if component has left the system
    private Semaphore componentPlacementMutex = new Semaphore(1, true);
    private Map<ComponentId, DeviceId> componentPlacement;
    private Map<ComponentId, Boolean> isBeingTransfered                         = new ConcurrentHashMap<>();

    // A mutex for searching/modifying the graph (deviceFreeSlots andwaitingForImport)
    private Semaphore GM = new Semaphore(1, true);
    private Map<DeviceId, Integer> deviceFreeSlots                              = new ConcurrentHashMap<>();
    private Map<DeviceId, LinkedList<ComponentTransfer> > waitingForImport      = new ConcurrentHashMap<>();

    // A transfer-specyfic semaphores and a transfer that I have to wait for (next) or is waiting for me (prev)
    // If I am not waiting for any transfer / no transfer is waiting for me than next/prev[me] = me
    private Map<ComponentTransfer, Semaphore> waitForStart                      = new ConcurrentHashMap<>(); 
    private Map<ComponentTransfer, Semaphore> waitForNextTransfer               = new ConcurrentHashMap<>(); 
    private Map<ComponentTransfer, ComponentTransfer> prevTransfer              = new ConcurrentHashMap<>();
    private Map<ComponentTransfer, ComponentTransfer> nextTransfer              = new ConcurrentHashMap<>();

    // private Map<ComponentTransfer, Boolean> lazyRemove = new ConcurrentHashMap<>();

    public MyStorageSystem(Map<DeviceId, Integer> deviceTotalSlots, Map<ComponentId, DeviceId> componentPlacement) {
        this.componentPlacement = new ConcurrentHashMap(componentPlacement);
        this.deviceTotalSlots = new ConcurrentHashMap(deviceTotalSlots);

        for (ComponentId componentId : componentPlacement.keySet()) {
            isBeingTransfered.put(componentId, false);
        }
        
        // Creating empty graph
        for (DeviceId deviceId : deviceTotalSlots.keySet()) {
            waitingForImport.put(deviceId, new LinkedList<>());
        }
    
        // Calculating starting number of free slots
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

        // Create and initialize transfer specyfic values
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
        // If there is free slot on destination device, take it and start
        if (deviceFreeSlots.get(transfer.getDestinationDeviceId()) > 0) { 
            deviceFreeSlots.put(transfer.getDestinationDeviceId(), deviceTotalSlots.get(transfer.getDestinationDeviceId()) - 1);
            GM.release();
            start(transfer);
            return;
        }

        // If there is a cycle, return it in order from source to destination without myself, otherwise return null
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
            // lazyRemove.put(ct, true);
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

    void checkIfTransferLegal(ComponentTransfer transfer) throws TransferException {
        if (componentPlacement.containsKey(transfer.getComponentId()) && isBeingTransfered.get(transfer.getComponentId())){
            componentPlacementMutex.release();
            throw new ComponentIsBeingOperatedOn(transfer.getComponentId());
        }
        if (transfer.getSourceDeviceId() == null && transfer.getDestinationDeviceId() == null){
            componentPlacementMutex.release();
            throw new IllegalTransferType(transfer.getComponentId());
        }
        if (transfer.getDestinationDeviceId() != null && !deviceTotalSlots.containsKey(transfer.getDestinationDeviceId())){
            componentPlacementMutex.release();
            throw new DeviceDoesNotExist(transfer.getDestinationDeviceId());
        }
        if (transfer.getSourceDeviceId() == null && componentPlacement.containsKey(transfer.getComponentId())){
            componentPlacementMutex.release();
            throw new ComponentAlreadyExists(transfer.getComponentId()); // SECOND CONSTRUCTOR
        }
        if (transfer.getSourceDeviceId() != null && (!componentPlacement.containsKey(transfer.getComponentId()) || !componentPlacement.get(transfer.getComponentId()).equals(transfer.getSourceDeviceId()))){
            componentPlacementMutex.release();
            throw new ComponentDoesNotExist(transfer.getComponentId(), transfer.getSourceDeviceId());
        }
        if (transfer.getSourceDeviceId() == transfer.getDestinationDeviceId()){
            componentPlacementMutex.release();
            throw new ComponentDoesNotNeedTransfer(transfer.getComponentId(), transfer.getDestinationDeviceId());
        }
    }

    void start(ComponentTransfer transfer) {
        acquire(GM);

        // If the transfer is not an addition and there isn't a prevTransfer chosen yet, choose the oldest waiting, remove and release them
        if (transfer.getSourceDeviceId() != null && prevTransfer.get(transfer) == transfer) {            
            if (!waitingForImport.get(transfer.getSourceDeviceId()).isEmpty()) {
                prevTransfer.put(transfer, waitingForImport.get(transfer.getSourceDeviceId()).getFirst());
                nextTransfer.put(waitingForImport.get(transfer.getSourceDeviceId()).getFirst(), transfer);
                waitingForImport.get(transfer.getSourceDeviceId()).removeFirst();
            }

            if (prevTransfer.get(transfer) != transfer)
                waitForStart.get(prevTransfer.get(transfer)).release();
        }
        GM.release();
        
        transfer.prepare();
        acquire(GM);
        // If a new transfer has showed up while we were preparing, find it now
        if (transfer.getSourceDeviceId() != null && prevTransfer.get(transfer) == transfer) {            
            if (!waitingForImport.get(transfer.getSourceDeviceId()).isEmpty()) {
                prevTransfer.put(transfer, waitingForImport.get(transfer.getSourceDeviceId()).getFirst());
                nextTransfer.put(waitingForImport.get(transfer.getSourceDeviceId()).getFirst(), transfer);
                waitingForImport.get(transfer.getSourceDeviceId()).removeFirst();
            }

            if (prevTransfer.get(transfer) != transfer)
                waitForStart.get(prevTransfer.get(transfer)).release();
        }
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

        // Clean transfer specyfic values
        waitForStart.remove(transfer);
        waitForNextTransfer.remove(transfer);
        prevTransfer.remove(transfer);
        nextTransfer.remove(transfer);
    }

    // null if no cycle, the cycle doesn't include itself
    List <ComponentTransfer> getCycle(ComponentTransfer transfer) {
        if (transfer.getSourceDeviceId() == null || transfer.getDestinationDeviceId() == null)
            return null;

        Map<DeviceId, Boolean> visited = new HashMap<>();
        for (DeviceId deviceId : deviceTotalSlots.keySet()) {
            visited.put(deviceId, false);
        }

        LinkedList <ComponentTransfer> res = new LinkedList<>();

        if (dfs(transfer.getSourceDeviceId(), transfer.getDestinationDeviceId(), visited, res))
            return res;
        return null;
    }

    Boolean dfs(DeviceId current, DeviceId destination, Map<DeviceId, Boolean> visited, LinkedList<ComponentTransfer> res) {
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