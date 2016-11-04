package group_project.test001;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by andre_eggli on 10/3/16.
 */

public class WifiDataBuffer {

    LinkedBlockingQueue<byte[]> ToESP; // DataType "WifiPackage" is a seperate class in this package.
    LinkedBlockingQueue<byte[]> FromESP;

    public WifiDataBuffer()  {
        ToESP = new LinkedBlockingQueue<>(10); // Max Size = 10
        FromESP = new LinkedBlockingQueue<>(100); // Max 100 unprocessed Packages at same time allowed
    }

    public boolean enqueue_ToESP(byte[] packet) {
        return ToESP.add(packet);
    }

    public byte[] dequeue_ToESP(){
        return ToESP.poll();
    }

    public boolean enque_FromESP(byte[] packet) {
        return FromESP.add(packet);
    }

    public byte[] deque_FromESP() {
        return FromESP.poll();
    }

    public boolean isDataWaiting_ToESP() {
        return !ToESP.isEmpty();
    }

    public boolean isDataWaiting_FromESP() {
        return !FromESP.isEmpty();
    }

}
