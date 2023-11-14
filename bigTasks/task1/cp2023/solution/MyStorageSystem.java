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
    private Map <ComponentId, Semaphore> componentSemaphores;

    public MyStorageSystem(Map<DeviceId, Integer> deviceTotalSlots, Map<ComponentId, DeviceId> componentPlacement) {
        this.componentPlacement = Map.copyOf(componentPlacement);

        // create empty maps
        componentSemaphores = new HashMap<>(componentPlacement.size());
        devicesById = new HashMap<>(deviceTotalSlots.size());
        Map<DeviceId, Integer> deviceTakenSlots = new HashMap<>(deviceTotalSlots.size());

        // Put all devices in device taken slots with 0 taken slots
        for (DeviceId deviceId : deviceTotalSlots.keySet()) {
            deviceTakenSlots.put(deviceId, 0);
        }

        // update taken slots of all devices
        // create semaphores for components
        for (Map.Entry<ComponentId, DeviceId> entry : componentPlacement.entrySet()) {
            deviceTakenSlots.put(entry.getValue(), deviceTakenSlots.get(entry.getValue()) + 1);
            componentSemaphores.put(entry.getKey(), new Semaphore(1));
        }

        // Create new devices and map them to id
        for (Map.Entry<DeviceId, Integer> entry : deviceTotalSlots.entrySet()) {
            devicesById.put(entry.getKey(), new Device(entry.getValue(), deviceTakenSlots.get(entry.getKey())));
        }
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
        try {
            destinationDevice.waitForMyTurn(transfer);
        } catch (InterruptedException e) {
            throw new RuntimeException("panic: unexpected thread interruption");
        }
        transfer.perform();

        componentPlacement.put(transfer.getComponentId(), transfer.getDestinationDeviceId());
    }

    private void executeMove(ComponentTransfer transfer) throws TransferException { 
        Device sourceDevice = devicesById.get(transfer.getSourceDeviceId());
        Device destinationDevice = devicesById.get(transfer.getDestinationDeviceId());
        
        transfer.prepare();
        sourceDevice.freeSpace();
        try {
            destinationDevice.waitForMyTurn(transfer);
        } catch (InterruptedException e) {
            throw new RuntimeException("panic: unexpected thread interruption");
        }
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