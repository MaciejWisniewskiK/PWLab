package mw448595;
import java.util.*;

public class BlockingQueue<T> {

    private LinkedList<T> que;
    private int capacity;

    public BlockingQueue(int capacity) {
        this.capacity = capacity;
        que = new LinkedList<T>();
    }

    public synchronized T take() throws InterruptedException {
        while (que.size() == 0) {
            wait();
        }
        T item = que.getFirst();
        que.removeFirst();
        notifyAll();
        return item;
    }

    public synchronized void put(T item) throws InterruptedException {
        while (que.size() == capacity) {
            wait();
        }
        que.addLast(item);
        notifyAll();
    }

    public synchronized int getSize() {
        return que.size();
    }

    public int getCapacity() {
        return capacity;
    }
}