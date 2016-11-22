package group_project.test001;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import java.lang.reflect.Method;

/**
 * Created by André Eggli on 20.11.16.
 */

// Quelle: http://android-coding.blogspot.ch/2011/11/interactive-between-activity-and.html
// Quelle: http://stackoverflow.com/questions/13124115/starting-android-service-already-running
public class CommunicationService extends Service {

    WifiManager wifi_manager;
    Boolean WifiWasOnWhenServiceWasStarted = false;
    static WifiDataBuffer wifiDataBuffer = new WifiDataBuffer();
    TCP_Data_dequeue_Thread TCP_Data_Sender = new TCP_Data_dequeue_Thread();
    static TCP_SERVER Socket = new Fake_TCP_Server(wifiDataBuffer); // Initialise Fake TCP to test
    // static TCP_SERVER Socket = new TCPServer(wifiDataBuffer); // Initialise real TCP_Server to test ESP8266
    IntentListenerForActivity ListenerForActivity; // receives Data from Activity via Broadcast
    private static final String LOG_TAG = "Service";

    public static final String ACTION_FROM_ACTIVITY = "ACTION_FROM_ACTIVITY";
    public static final String TRIGGER_Serv2Act = "Service -> Activity";
    public static final String COMMAND_Act2Serv = "COMMAND_Act2Serv";
    public static final String TRIGGER_Act2Serv = "Activity -> Service";
    public static final int CMD_STOP = 1;
    public static final String DATA_BACK = "DATA_BACK";

    boolean running = true;

    byte[] initData;

    public CommunicationService() {
        Log.d(LOG_TAG, "Constructor called");
        TCP_Data_Sender.start(); // dequeues Data from TCPServer and Broadcasts it to Activity
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // Is never used
        Log.d(LOG_TAG, "onBind called");
        Toast.makeText(this, "onBind called", Toast.LENGTH_SHORT).show();
        return null;
    }

    @Override
    public void onCreate() {
        wifi_manager = (WifiManager) this.getSystemService(this.WIFI_SERVICE);
        WifiConfiguration wifi_configuration = null;
        if(wifi_manager.getWifiState() == 2 || wifi_manager.getWifiState() == 3) {// Enum Constantes for Wifi_enabling and Wifi_enabled
            WifiWasOnWhenServiceWasStarted = true;
            Log.d(LOG_TAG,"Wifi was turned on @ OnCreate");
        }
        else {
            WifiWasOnWhenServiceWasStarted = false; Log.d(LOG_TAG, "Wifi was turned off @ OnCreate");}
        Log.d(LOG_TAG, "onCreate");
        ListenerForActivity = new IntentListenerForActivity();

        super.onCreate();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_FROM_ACTIVITY);
        registerReceiver(ListenerForActivity, intentFilter);
        wifi_manager = (WifiManager) this.getSystemService(this.WIFI_SERVICE);
        WifiConfiguration wifi_configuration = null;
        wifi_manager.setWifiEnabled(false);
        try {
            // Source http://stackoverflow.com/questions/13946607/android-how-to-turn-on-hotspot-in-android-programmatically
            Method method = wifi_manager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
            method.invoke(wifi_manager, wifi_configuration, true);
            Log.d(LOG_TAG,"turned Hotspot on");
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        Toast.makeText(this, "Service started", Toast.LENGTH_SHORT).show();

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show();
        Log.d(LOG_TAG, "onDestroy called");
        // turn Hotspot off.
        try {
            Method method = wifi_manager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
            WifiConfiguration wifi_configuration = null;
            method.invoke(wifi_manager, wifi_configuration, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // turn Wifi back on.
        wifi_manager.setWifiEnabled(WifiWasOnWhenServiceWasStarted);
        this.unregisterReceiver(ListenerForActivity);
        super.onDestroy();
    }

    private void SendDataToActivity(byte[] data) {
        Intent intent = new Intent();
        intent.setAction(TRIGGER_Serv2Act);
        intent.putExtra(DATA_BACK, data);
        sendBroadcast(intent);

    }

    public class TCP_Data_dequeue_Thread extends Thread{
        final String Log_tag = "Service_dequeue_Thread";
        @Override
        public void run() {
            Log.d(Log_tag, "Thread started");
            while(running){ // && !TCP_Data_Sender.isInterrupted()
                try {
                    if(!wifiDataBuffer.isDataWaiting_FromESP()){
                        Thread.sleep(50);
                    }
                    else {
                        SendDataToActivity(wifiDataBuffer.deque_FromESP());
                    }
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            Log.d(Log_tag, "Thread ended, going to call onDestroy of Service");
            stopSelf(); // Stop the Service
        }
    }
    public class IntentListenerForActivity extends BroadcastReceiver {
        @Override
        public void onReceive(Context arg0, Intent intent) {
            if(intent.hasExtra(COMMAND_Act2Serv)){
                int hostCmd = intent.getIntExtra(COMMAND_Act2Serv, 0);
                if(hostCmd == CMD_STOP){
                    running = false;
                    // stopSelf();
                }
            }
            else if (intent.hasExtra(TRIGGER_Act2Serv)){
                byte[] TriggerPack = intent.getByteArrayExtra(TRIGGER_Act2Serv);
                wifiDataBuffer.enqueue_ToESP(TriggerPack);
                initData = TriggerPack;
            }
        }
    }

}





