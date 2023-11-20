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

import cp2023.exceptions.TransferException;

public class MyStorageSystem implements StorageSystem {

    private Map<DeviceId, Integer> deviceTotalSlots;

    private Semaphore componentPlacementMutex = new Semaphore(1, true);
    private Map<ComponentId, DeviceId> componentPlacement; // null -> currently being transfered


    private Semaphore GM = new Semaphore(1, true); // Graph mutex
    private Map<DeviceId, Integer> deviceFreeSlots = new HashMap<>();
    private Map<DeviceId, LinkedList<ComponentTransfer> > waitingForImport = new HashMap<>();

    private Map<ComponentTransfer, Semaphore> waitForStart = new ConcurrentHashMap<>(); 
    private Map<ComponentTransfer, ComponentTransfer> prevTransfer = new ConcurrentHashMap<>(); 

    private Map<ComponentTransfer, Boolean> lazyRemove = new ConcurrentHashMap<>();

    public MyStorageSystem(Map<DeviceId, Integer> deviceTotalSlots, Map<ComponentId, DeviceId> componentPlacement) {
        this.componentPlacement = new HashMap(componentPlacement);
        this.deviceTotalSlots = new HashMap(deviceTotalSlots);
    
        for (DeviceId deviceId : deviceTotalSlots.keySet()) {
            waitingForImport.put(deviceId, new LinkedList<>());
        }

        deviceFreeSlots = new HashMap(deviceTotalSlots); // set the number of free slots to number of total slots and decrement them for eevery component stored
        for (Map.Entry<ComponentId, DeviceId> entry : componentPlacement.entrySet()) {
            deviceFreeSlots.put(entry.getValue(), deviceFreeSlots.get(entry.getValue()) - 1); 
        }
    }

    public void execute(ComponentTransfer transfer) throws TransferException {
        checkIfTransferLegal(transfer); // throws exception if not

        //GM.acquire();
        waitForStart.put(transfer, new Semaphore(1, true)); // create my semaphore
        prevTransfer.put(transfer, null);
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
            waitingForImport.get(transfer.getDestinationDeviceId()).add(transfer);
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
        }
        prevTransfer.put(currentCT, transfer);

        //GM.release();

        for (ComponentTransfer ct : cycle) {
            waitForStart.get(ct).release();
        }

        start(transfer);
    }

    void checkIfTransferLegal(ComponentTransfer transfer) {
        throw new RuntimeException("Not Implemented");
    }

    void start(ComponentTransfer transfer) {
        // remove waitforstart and prevtransfer
        throw new RuntimeException("Not Implemented");
    }

    List <ComponentTransfer> getCycle(ComponentTransfer transfer) {
        throw new RuntimeException("Not Implemented");
    }
}