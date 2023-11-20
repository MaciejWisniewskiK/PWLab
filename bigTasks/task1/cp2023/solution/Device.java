package cp2023.solution;

import java.util.Queue;
import java.util.LinkedList;

import cp2023.base.ComponentTransfer;

public class Device {

    private LinkedList<MyTransfer> waitingExport = new LinkedList<>();
    private LinkedList<MyTransfer> waitingImport = new LinkedList<>();
    private Integer capacity, freeSlots;

    public Device(Integer capacity, Integer takenSlots) {
        this.capacity = capacity;
        this.freeSlots = capacity - takenSlots;
    }
    
}