package cp2023.solution;

import java.util.Queue;
import java.util.LinkedList;

import cp2023.base.ComponentTransfer;

public class Device {

    private Queue<ComponentTransfer> waiting = new LinkedList<>();
    private Integer freeSpace, capacity;

    public Device(Integer capacity, Integer takenSpace) {
        this.capacity = capacity;
        freeSpace = capacity - takenSpace;
    }

    public synchronized void askForSpace(ComponentTransfer transfer) {
        waiting.add(transfer);
    }
    
    public synchronized Boolean isMyTurn(ComponentTransfer transfer) {
        if (!freeSpace) return false;
        if (waiting.peek() != transfer) return false;
        return true;
    }

    public synchronized void startExecuting() {
        waiting.remove();
    }

    public synchronized void freeSpace() {
        freeSpace++;
        notifyAll();
    }
}