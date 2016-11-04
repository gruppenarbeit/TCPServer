package group_project.test001;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by andre_eggli on 10/3/16.
 */

public class WifiDataBuffer {

    LinkedBlockingQueue<WifiPackage> ToESP;
    LinkedBlockingQueue<WifiPackage> FromESP;

    public WifiDataBuffer()  {
        ToESP = new LinkedBlockingQueue<>(10);
        FromESP = new LinkedBlockingQueue<>(500);
    }

    public boolean enqueue_ToESP(WifiPackage Pack) {
        if (Pack.getRowData().length  == 0){ // Do not enqueue empty Strings
            return true;
        }
        return ToESP.add(Pack);
    }

    public WifiPackage dequeue_ToESP(){
        return ToESP.poll();
    }

    public boolean enque_FromESP(WifiPackage Pack) {
        if (Pack.getRowData().length  == 0){ // Do not enqueue empty Strings
            return true;
        }
        return FromESP.add(Pack);
    }

    public WifiPackage deque_FromESP() {
        return FromESP.poll();
    }

    public boolean isDataWaiting_ToESP() {
        return !ToESP.isEmpty();
    }

    public boolean isDataWaiting_FromESP() {
        return !FromESP.isEmpty();
    }

}
