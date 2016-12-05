package group_project.test001;

// CommunicationService does:
// 1. Turn off Wifi
// 2. Turn Hotspot on
// 3. start an instance of TCP_SERVER (an Interface)
// 4. Pass TrigerPackages (from BroadcastReceiver) to the TCP_SERVER (via WifiDataBuffer)
// 5. Send measurement-data (from WifiDataBuffer) back to an Activity (via BroadcastSender)
// 6. Stop itself when stopservice(intent) is called by an activity
// 7. Turn the hotspot off
// 8. Turn Wifi back on, if it was on on startup of service

/**
 * Created by André Eggli on 20.10.16.
 */

// Source: http://android-coding.blogspot.ch/2011/11/interactive-between-activity-and.html
// Source: http://stackoverflow.com/questions/13124115/starting-android-service-already-running
// Source: http://stackoverflow.com/questions/6394599/android-turn-on-off-wifi-hotspot-programmatically


import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;
import java.lang.reflect.Method;


public class CommunicationService extends Service {

    WifiManager wifi_manager;
    Boolean WifiWasOnWhenServiceWasStarted = false;
    static final WifiDataBuffer wifiDataBuffer = new WifiDataBuffer();
    TCP_Data_dequeue_Thread dataSenderThread = new TCP_Data_dequeue_Thread();

    // final static TCP_SERVER Socket = new Fake_TCP_Server(wifiDataBuffer); // Initialise Fake TCP to test
    final static TCP_SERVER Socket = new TCPServer(wifiDataBuffer); // Initialise real TCP_Server to test ESP8266
    // final static TCP_SERVER Socket = new Excel_Facke_TCP_Server(wifiDataBuffer);

    IntentListenerForActivity ListenerForActivity; // receives Data from Activity via Broadcast
    private static final String LOG_TAG = "Service";
    public static final String ACTION_FROM_ACTIVITY = "ACTION_FROM_ACTIVITY";
    public static final String TRIGGER_Serv2Act = "Service -> Activity";
    public static final String COMMAND_Act2Serv = "COMMAND_Act2Serv";
    public static final int CMD_STOP = 1;
    public static final int CMD_getCALI = 2;
    public static final String TRIGGER_Act2Serv = "Activity -> Service";
    public static final String DATA_BACK = "DATA_BACK";
    boolean running = true;
    byte[] callipack;

    public CommunicationService() {
        Log.d(LOG_TAG, "Constructor called");
        dataSenderThread.start(); // dequeues Data from TCPServer and Broadcasts it to Activity
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Is never used but has to be implemented
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

        Toast.makeText(this, "Service started", Toast.LENGTH_SHORT).show();

        super.onCreate();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(LOG_TAG, "onStartCommand");
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_FROM_ACTIVITY);
        registerReceiver(ListenerForActivity, intentFilter);
        super.onStartCommand(intent, flags, startId);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(LOG_TAG, "onDestroy called");
        running = false;
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
        Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show();
        super.onDestroy();
    }

    private void SendDataToActivity(byte[] data) {
        Intent intent = new Intent();
        intent.setAction(TRIGGER_Serv2Act);
        intent.putExtra(DATA_BACK, data);
        sendBroadcast(intent);
    }

    private byte[] split_packet (int start, int end, byte[] packet){
        int length = end - start + 1;
        byte[] splitted = new byte[length];
        for (int i = 0; i < length; i++){
            splitted[i] = packet[i + start];
        }
        return splitted;
    }

    public class TCP_Data_dequeue_Thread extends Thread{
        final String Log_tag = "Service_dequeue_Thread";
        @Override
        public void run() {
            Log.d(Log_tag, "Thread started");
            while(running){ // && !dataSenderThread.isInterrupted()
                // Log.d(LOG_TAG, "Thread runns");
                try {
                    if(!wifiDataBuffer.isDataWaiting_FromESP()){
                        Thread.sleep(50);
                    }
                    else {
                        byte[] received = wifiDataBuffer.deque_FromESP();
                        if (new String(split_packet(4, 7, received)).equals("CALD")){
                            callipack = received;
                            Log.d(Log_tag, "got CalTable from ESP");
                        }
                        else {
                            SendDataToActivity(received);
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            // case 'running == false'
            Log.d(Log_tag, "Thread ended, going to call onDestroy of Service");
            stopSelf(); // Stop the Service
            // onDestroy of Service is called to restore WifiSettings
        }
    }

    public class IntentListenerForActivity extends BroadcastReceiver {
        @Override
        public void onReceive(Context arg0, Intent intent) {
            Log.d(LOG_TAG, "onReceive");
            if(intent.hasExtra(COMMAND_Act2Serv)) {
                int hostCmd = intent.getIntExtra(COMMAND_Act2Serv, 0);
                if (hostCmd == CMD_STOP) {
                    running = false;
                    // stopSelf();
                } else if (hostCmd == CMD_getCALI) {
                    SendDataToActivity(callipack);
                    Log.d(LOG_TAG, "someActivity requested callipack");
                }
            }
            else if (intent.hasExtra(TRIGGER_Act2Serv)){
                byte[] TriggerPack = intent.getByteArrayExtra(TRIGGER_Act2Serv);
                wifiDataBuffer.enqueue_ToESP(TriggerPack);
            }
        }
    }
}




