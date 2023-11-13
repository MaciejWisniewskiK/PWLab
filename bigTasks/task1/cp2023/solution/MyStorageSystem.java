package cp2023.solution;

import java.util.Map;
import java.util.HashMap;

import cp2023.base.ComponentId;
import cp2023.base.DeviceId;
import cp2023.base.StorageSystem;
import cp2023.base.ComponentTransfer;

import cp2023.exceptions.TransferException;

public class MyStorageSystem implements StorageSystem {

    private Map <DeviceId, Device> devicesById;
    private Map <ComponentId, DeviceId> componentPlacement;

    public MyStorageSystem(
            Map<DeviceId, Integer> deviceTotalSlots,
            Map<ComponentId, DeviceId> componentPlacement) {
            
        devicesById = new HashMap(deviceTotalSlots.size());
        throw new RuntimeException("Not Implemented");
    }

    public void execute(ComponentTransfer transfer) throws TransferException {
        throw new RuntimeException("Not Implemented");
        // check if transfer is legal
        // mark component as beingOperatedOn
        // execute correct type of transfer
        // mark component as no longer beingOperatedOn
    }

    private void executeAdd(ComponentTransfer transfer) throws TransferException {
        throw new RuntimeException("Not Implemented");
    }

    private void executeTransfer(ComponentTransfer transfer) throws TransferException {
        Device D1 = devicesById.get(transfer.getSourceDeviceId());
        Device D2 = devicesById.get(transfer.getDestinationDeviceId());
        
        transfer.prepare();
        D1.freeSpace();

        D2.askForSpace();
        while (!D2.isMyTurn()) { 
            
        }
        // Wait for slot on D2
        // perform
        // change component's location
        throw new RuntimeException("Not Implemented");
    }

    private void executeRemove(ComponentTransfer transfer) throws TransferException {
        throw new RuntimeException("Not Implemented");
    }
}