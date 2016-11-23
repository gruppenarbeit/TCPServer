package group_project.test001;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created by andre_eggli on 11/23/16.
 */

public class MyActivityReceiver extends BroadcastReceiver {
    final String LOG_TAG;
    final WifiDataBuffer wifiDataBufffer;

    public MyActivityReceiver(String LOG_TAG, WifiDataBuffer wifiDataBuffer){
        this.LOG_TAG = LOG_TAG;
        this.wifiDataBufffer = wifiDataBuffer;
    }
    @Override
    public void onReceive(Context arg0, Intent data) {
        Log.d(LOG_TAG, "MyActivityReceiver in onReceive");
        byte[] orgData = data.getByteArrayExtra(CommunicationService.DATA_BACK);
        if (orgData != null) {
            wifiDataBufffer.enque_FromESP(orgData);
        }
    }
}
