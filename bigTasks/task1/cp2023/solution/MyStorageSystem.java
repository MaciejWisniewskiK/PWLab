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

    private Semaphore componentPlacementMutex = new Semaphore(1, true);
    private Map<ComponentId, DeviceId> componentPlacement; // null -> currently being transfered


    private Semaphore GM = new Semaphore(1, true); // Graph mutex
    private Map<DeviceId, Integer> deviceFreeSlots = new ConcurrentHashMap<>();
    private Map<DeviceId, LinkedList<ComponentTransfer> > waitingForImport = new ConcurrentHashMap<>();

    private Map<ComponentTransfer, Semaphore> waitForStart = new ConcurrentHashMap<>(); 
    private Map<ComponentTransfer, ComponentTransfer> prevTransfer = new ConcurrentHashMap<>();
    private Map<ComponentTransfer, ComponentTransfer> nextTransfer = new ConcurrentHashMap<>();

    private Map<ComponentTransfer, Boolean> lazyRemove = new ConcurrentHashMap<>();

    public MyStorageSystem(Map<DeviceId, Integer> deviceTotalSlots, Map<ComponentId, DeviceId> componentPlacement) {
        this.componentPlacement = new ConcurrentHashMap(componentPlacement);
        this.deviceTotalSlots = new ConcurrentHashMap(deviceTotalSlots);
    
        for (DeviceId deviceId : deviceTotalSlots.keySet()) {
            waitingForImport.put(deviceId, new LinkedList<>());
        }

        deviceFreeSlots = new ConcurrentHashMap(deviceTotalSlots); // set the number of free slots to number of total slots and decrement them for eevery component stored
        for (Map.Entry<ComponentId, DeviceId> entry : componentPlacement.entrySet()) {
            deviceFreeSlots.put(entry.getValue(), deviceFreeSlots.get(entry.getValue()) - 1); 
        }
    }

    public void execute(ComponentTransfer transfer) throws TransferException {
        checkIfTransferLegal(transfer); // throws exception if not
        // COMPONENT PLACEMENT!!!!!!!!!!!!!!!!!!!!!!!!!!
        //GM.acquire();
        waitForStart.put(transfer, new Semaphore(1, true)); // create my semaphore
        prevTransfer.put(transfer, transfer);
        nextTransfer.put(transfer, transfer);
        //GM.release();

        if (transfer.getDestinationDeviceId() == null) { // If the transfer is a removal of a file, immidiately start
            start(transfer);
            return;
        }
 
        try {
            GM.acquire();
        } catch(InterruptedException e) {
            throw new RuntimeException("panic: unexpected thread interruption");
        }

        if (deviceFreeSlots.get(transfer.getDestinationDeviceId()) > 0) { 
            deviceFreeSlots.put(transfer.getDestinationDeviceId(), deviceTotalSlots.get(transfer.getDestinationDeviceId()) - 1); // take one slot
            GM.release();
            start(transfer);
            return;
        }

        List<ComponentTransfer> cycle = getCycle(transfer); // null if no cycle, doesn't include itself

        if (cycle == null) {
            waitingForImport.get(transfer.getDestinationDeviceId()).addLast(transfer);
            GM.release();

            try {
                waitForStart.get(transfer).acquire();
            } catch(InterruptedException e) {
                throw new RuntimeException("panic: unexpected thread interruption");
            }
            start(transfer);
            return;
        }

        for (ComponentTransfer ct : cycle) {
            lazyRemove.put(ct, true);
        }
        GM.release();

        // assumes _transfer_ is from the first elements DD to the last elements SD
        ComponentTransfer nextCT = transfer, currentCT = null;
        for (ComponentTransfer ct : cycle) {
            currentCT = ct;
            prevTransfer.put(nextCT, currentCT);
            nextTransfer.put(currentCT, nextCT);
        }
        prevTransfer.put(currentCT, transfer);
        nextTransfer.put(transfer, currentCT);

        //GM.release();

        for (ComponentTransfer ct : cycle) {
            waitForStart.get(ct).release();
        }

        start(transfer);
    }

    void checkIfTransferLegal(ComponentTransfer transfer) throws TransferException {
        if (transfer.getSourceDeviceId() == null && transfer.getDestinationDeviceId() == null)
            throw new IllegalTransferType(transfer.getComponentId());
        if (transfer.getDestinationDeviceId() != null && !deviceTotalSlots.containsKey(transfer.getDestinationDeviceId())) 
            throw new DeviceDoesNotExist(transfer.getDestinationDeviceId());
        if (transfer.getSourceDeviceId() == null && componentPlacement.containsKey(transfer.getComponentId()))
            throw new ComponentAlreadyExists(transfer.getComponentId()); // SECOND CONSTRUCTOR
        if (transfer.getSourceDeviceId() != null && (!componentPlacement.containsKey(transfer.getComponentId()) || !componentPlacement.get(transfer.getComponentId()).equals(transfer.getSourceDeviceId()))) 
            throw new ComponentDoesNotExist(transfer.getComponentId(), transfer.getSourceDeviceId());
        if (componentPlacement.containsKey(transfer.getComponentId()) && componentPlacement.get(transfer.getComponentId()) == null)
            throw new ComponentIsBeingOperatedOn(transfer.getComponentId());
        if (transfer.getSourceDeviceId() == transfer.getDestinationDeviceId())
            throw new ComponentDoesNotNeedTransfer(transfer.getComponentId(), transfer.getDestinationDeviceId());
    }

    void start(ComponentTransfer transfer) {

        if (transfer.getSourceDeviceId() != null && prevTransfer.get(transfer) == transfer) {
            try {
                GM.acquire();
            } catch(InterruptedException e) {
                throw new RuntimeException("panic: unexpected thread interruption");
            }

            while   (!waitingForImport.get(transfer.getSourceDeviceId()).isEmpty() &&                       // someone is waiting
                    lazyRemove.containsKey(waitingForImport.get(transfer.getSourceDeviceId()).getFirst())){ // the first in que is marked to be removed
                lazyRemove.remove(waitingForImport.get(transfer.getSourceDeviceId()).getFirst());
                waitingForImport.get(transfer.getSourceDeviceId()).removeFirst();
            }
            
            if (!waitingForImport.get(transfer.getSourceDeviceId()).isEmpty()) {
                prevTransfer.put(transfer, waitingForImport.get(transfer.getSourceDeviceId()).getFirst());
                nextTransfer.put(waitingForImport.get(transfer.getSourceDeviceId()).getFirst(), transfer);
                waitingForImport.get(transfer.getSourceDeviceId()).removeFirst();
            }

            GM.release();

            if (prevTransfer.get(transfer) != transfer)
                waitForStart.get(prevTransfer.get(transfer)).release();
        }

        transfer.prepare();
        if (prevTransfer.get(transfer) != transfer)
            waitForStart.get(prevTransfer.get(transfer)).release();
        else if (transfer.getSourceDeviceId() != null) {
            try {
                GM.acquire();
            } catch(InterruptedException e) {
                throw new RuntimeException("panic: unexpected thread interruption");
            }
            deviceFreeSlots.put(transfer.getSourceDeviceId(), deviceFreeSlots.get(transfer.getSourceDeviceId()) - 1);
            GM.release();
        }

        if (nextTransfer.get(transfer) != transfer) {
            try {
                waitForStart.get(transfer).acquire();
            } catch(InterruptedException e) {
                throw new RuntimeException("panic: unexpected thread interruption");
            }
        }
        transfer.perform();


        waitForStart.remove(transfer);
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
            if (lazyRemove.containsKey(edge) || edge.getSourceDeviceId() == null || visited.get(edge.getSourceDeviceId()))
                continue;
            res.addLast(edge);
            if (dfs(edge.getSourceDeviceId(), destination, visited, res))
                return true;
            res.removeLast();
        }

        return false;
    }
}