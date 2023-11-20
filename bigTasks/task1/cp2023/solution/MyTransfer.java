package cp2023.solution;

import cp2023.base.ComponentId;
import cp2023.base.DeviceId;
import cp2023.base.ComponentTransfer;

public class MyTransfer {
    private ComponentTransfer transfer;

    public MyTransfer(ComponentTransfer transfer) {
        this.transfer = transfer;
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
}