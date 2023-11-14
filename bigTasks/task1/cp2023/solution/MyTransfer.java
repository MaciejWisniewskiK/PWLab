package cp2023.solution;

import cp2023.base.ComponentId;
import cp2023.base.DeviceId;
import cp2023.base.ComponentTransfer;

public class MyTransfer {
    private ComponentTransfer transfer;
    private volatile Boolean executed;

    public MyTransfer(ComponentTransfer transfer) {
        this.transfer = transfer;
        executed = false;
    }

    public ComponentId getComponentId() {
        return transfer.getComponentId();
    }
    
    public DeviceId getSourceDeviceId() {
        return transfer.getSourceDeviceId();
    }
    
    public DeviceId getDestinationDeviceId() {
        return transfer.getDestinationDeviceId();
    }
    
    public void prepare() {
        transfer.prepare();
    }
    
    public void perform() {
        transfer.perform();
    }

    public synchronized void markAsExecuted() {
        beenExecuted = true;
    }

    public synchronized Boolean getExecuted() {
        return executed;
    }
}