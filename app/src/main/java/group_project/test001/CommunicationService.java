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
public class CommunicationService extends Service {

    WifiManager wifi_manager;
    Boolean WifiWasOn;

    public static final String LOG_TAG = "Service";
    MyServiceReceiver myServiceReceiver;
    final static String MY_ACTION_FROM_ACTIVITY = "MY_ACTION_FROM_ACTIVITY";

    public static final String CMD = "CMD";
    public static final String TRIGGER = "Trigger";
    public static final int CMD_STOP = 1;
    boolean running;

    byte[] initData;

    public CommunicationService() {
        Log.d(LOG_TAG, "Constructor called");
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
            WifiWasOn = true;
            Log.d(LOG_TAG,"Wifi was turned on @ OnCreate");
        }
        else {WifiWasOn = false; Log.d(LOG_TAG, "Wifi was turned off @ OnCreate");}
        Log.d(LOG_TAG, "onCreate");
        myServiceReceiver = new MyServiceReceiver();
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        Toast.makeText(this, "service stoped/app closed", Toast.LENGTH_SHORT).show();
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
        wifi_manager.setWifiEnabled(WifiWasOn);
        this.unregisterReceiver(myServiceReceiver);
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MY_ACTION_FROM_ACTIVITY);
        registerReceiver(myServiceReceiver, intentFilter);
        running = true;

        wifi_manager = (WifiManager) this.getSystemService(this.WIFI_SERVICE);
        WifiConfiguration wifi_configuration = null;
        if(wifi_manager.getWifiState() == 2 || wifi_manager.getWifiState() == 3) {// Enum Constantes for Wifi_enabling and Wifi_enabled
            Log.d(LOG_TAG,"OnStartCommand: Wifi was turned on @ onStart");
        }
        else {Log.d(LOG_TAG, "OnStartCommand: Wifi was turned off @ onStart");}

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

        Toast.makeText(this, "service started", Toast.LENGTH_SHORT).show();

        MyThread myThread = new MyThread();
        myThread.start();

        return super.onStartCommand(intent, flags, startId);
    }

    public class MyThread extends Thread{

        @Override
        public void run() {
            // TODO Auto-generated method stub
            int i = 0;
            while(running){
                try {
                    Thread.sleep(5000);
                    Intent intent = new Intent();
                    intent.setAction(LOG_TAG);

                    intent.putExtra("DATAPASSED", i);
                    intent.putExtra("DATA_BACK", initData);

                    sendBroadcast(intent);

                    i++;
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            stopSelf();
        }

    }
    public class MyServiceReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context arg0, Intent intent) {
            if(intent.hasExtra(CMD)){
                int hostCmd = intent.getIntExtra(CMD, 0);
                if(hostCmd == CMD_STOP){
                    running = false;
                    stopSelf();

                    Toast.makeText(CommunicationService.this,
                            "Service stopped by main Activity!",
                            Toast.LENGTH_LONG).show();
                }
            }
            else if (intent.hasExtra(TRIGGER)){
                initData = intent.getByteArrayExtra(TRIGGER);
            }
        }
    }

}





