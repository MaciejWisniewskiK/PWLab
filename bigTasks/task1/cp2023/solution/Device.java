package cp2023.solution;

import java.util.Queue;
import java.util.LinkedList;

import cp2023.base.ComponentTransfer;

public class Device {

    private Queue<MyTransfer> waitingImport = new LinkedList<>();
    private LinkedList<MyTransfer> waitingExport = new LinkedList<>();

    private Integer freeSpace, capacity;

    public Device(Integer capacity, Integer takenSpace) {
        this.capacity = capacity;
        freeSpace = capacity - takenSpace;
    }

    public synchronized Boolean waitForSlot(MyTransfer transfer) throws InterruptedException {
        waitingImport.add(transfer);

        while (freeSpace == 0 || waiting.peek() != transfer) {
            wait();
        } 

        waitingImport.remove();

        Boolean transferWasExecutedBefore;
        synchronized (transfer) {
            transferWasExecutedBefore = transfer.getExecuted();
            transfer.markAsExecuted();
        }

        if (transferWasExecutedBefore) {        
            notifyAll();
            return false;
        }

        freeSpace--;
        notifyAll();
        return true;
    }

    public synchronized void freeSlot() {
        freeSpace++;
        notifyAll();
    }

    public void giveSlotTo(MyTransfer transfer) {
        synchronized (transfer) {
            
        }
    }


    /*public synchronized void waitForMyTurn(ComponentTransfer transfer) throws InterruptedException {
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
    }*/
}