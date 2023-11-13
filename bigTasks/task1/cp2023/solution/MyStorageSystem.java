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
        // check if transfer is legal
        // mark component as beingOperatedOn
        // execute correct type of transfer
        // mark component as no longer beingOperatedOn
        throw new RuntimeException("Not Implemented");
    }

    private void executeAdd(ComponentTransfer transfer) throws TransferException {
        Device destinationDevice = devicesById.get(transfer.getDestinationDeviceId());

        transfer.prepare();
        destinationDevice.waitForMyTurn();
        transfer.perform();

        componentPlacement.put(transfer.getComponentId(), transfer.getDestinationDeviceId());
    }

    private void executeMove(ComponentTransfer transfer) throws TransferException { 
        Device sourceDevice = devicesById.get(transfer.getSourceDeviceId());
        Device destinationDevice = devicesById.get(transfer.getDestinationDeviceId());
        
        transfer.prepare();
        sourceDevice.freeSpace();
        destinationDevice.waitForMyTurn();
        transfer.perform();

        componentPlacement.put(transfer.getComponentId(), transfer.getDestinationDeviceId());
    }

    private void executeRemove(ComponentTransfer transfer) throws TransferException {
        Device sourceDevice = devicesById.get(transfer.getSourceDeviceId());

        transfer.prepare();
        sourceDevice.freeSpace();
        transfer.perform();

        componentPlacement.remove(transfer.getComponentId());
    }
}