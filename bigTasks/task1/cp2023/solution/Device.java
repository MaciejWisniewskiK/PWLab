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

    public synchronized void waitForMyTurn(ComponentTransfer transfer) throws InterruptedException {
        waiting.add(transfer);

        while (freeSpace == 0 || waiting.peek() != transfer) {
            wait();
        }

        freeSpace--;
        waiting.remove();
    }
    
    public synchronized void freeSpace() {
        freeSpace++;
        notifyAll();
    }
}