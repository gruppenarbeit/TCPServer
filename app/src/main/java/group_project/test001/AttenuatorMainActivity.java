package group_project.test001;

import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import java.lang.reflect.Method;


public class AttenuatorMainActivity extends AppCompatActivity {

    Button sendScan;
    Button SendLive;
    Button StopLive;
    Button SendCali;
    Button SendDETV;
    TextView SocketText;

    // These Attributes are also needed in the "real" App
    WifiManager wifi_manager;
    TCPServer Socket;
    WifiDataBuffer wifiDataBuffer;
    Boolean WifiWasOn;

    // Dequeues an element from wifiDataBuffer and puts it on a TextView
    // Called by Thread t, see OnCreate
    public void update_Sockettext() { // TODO: Dequeueing elements schould be done by Matthias Thread
        if (wifiDataBuffer.isDataWaiting_FromESP()) {
            byte[] rowData = wifiDataBuffer.deque_FromESP();
            int maxleng = rowData.length;
            String str = "Messages received:\n";
            if (maxleng > 200) {
                maxleng = 200;
            }

            String messageType = "\nWifiPackage.toString: ";
            for (int i = 0; i < maxleng; i++) {
                messageType += (rowData[i] & 0xFF) + "#";
            }
            messageType += ",\n";

            SocketText.setText(str + messageType);
        }
    }


    // For sending Commands to the ESP.
    private void SendScanTrigger() { // Dev ID =
        byte[] ScanTriggerPack = {82, 68, 49, 54, 83, 67, 65, 78, 0, 0, 0, 125, 0, 80, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 80, 69, 78, 68};
        wifiDataBuffer.enqueue_ToESP(ScanTriggerPack);
    }

    private void SendLiveTrigger() {
        // byte[] LiveTriggerPack = {82,   68,   49,   54,   84,   73,   77,   69,    0,    0, 0, 125,    0,    0,    7,   65,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,   80,   69,   78,   68};
        byte[] LiveTriggerPack = {82, 68, 49, 54, 84, 73, 77, 69, 0, 0, 0, 125, 0, 0, 7, 65, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 80, 69, 78, 68};
        wifiDataBuffer.enqueue_ToESP(LiveTriggerPack);
    }

    private void SendLiveStop() {
        byte[] LiveStopPack = {82, 68, 49, 54, 84, 73, 77, 69, 0, 0, 0, 125, 0, 0, 7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 80, 69, 78, 68};
        wifiDataBuffer.enqueue_ToESP(LiveStopPack);
    }

    private void SendCaliTrigger() {
        byte[] calitrigger = {82, 68, 49, 54, 67, 65, 76, 68,0, 0, 0, 125, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 80, 69, 78, 68};
        wifiDataBuffer.enqueue_ToESP(calitrigger);
    }

    private void SendDETVTrigger() {
        byte[] DETVTrigger = {82, 68, 49, 54, 68, 69, 84, 86, 0, 0, 0, 125, 0, 4, 0, 2, 0, 20, 0, 60, 0, 100, 0, 0, 0, 0, 80, 0, 80, 69, 78, 68};
        wifiDataBuffer.enqueue_ToESP(DETVTrigger);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("MainActivity","in OnCreate of AttenuatorMainActivity");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        sendScan = (Button) findViewById(R.id.send_Scan);
        SendLive = (Button) findViewById(R.id.send_live);
        StopLive = (Button) findViewById(R.id.stop_live);
        SendCali = (Button) findViewById(R.id.send_calitrigger);
        SendDETV = (Button) findViewById(R.id.send_detail);
        SocketText = (TextView) findViewById(R.id.Sockettext);

        wifiDataBuffer = new WifiDataBuffer();
        Socket = new TCPServer(wifiDataBuffer); // Initialise TCPServer as well

        wifi_manager = (WifiManager) this.getSystemService(AttenuatorMainActivity.this.WIFI_SERVICE);
        WifiConfiguration wifi_configuration = null;
        if(wifi_manager.getWifiState() == 2 || wifi_manager.getWifiState() == 3) {// Enum Constantes for Wifi_enabling and Wifi_enabled
            WifiWasOn = true;
            Log.d("MainActivity","Wifi was turned on @ OnCreate");
        }
        else {WifiWasOn = false; Log.d("MainActivity","Wifi was turned off @ OnCreate");}


        sendScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SendScanTrigger();
            }
        });

        SendLive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SendLiveTrigger();
            }
        });

        StopLive.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                SendLiveStop();
            }
        });

        SendCali.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                SendCaliTrigger();
            }
        });

        SendDETV.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                SendDETVTrigger();
            }
        });

    }

    @Override
    public void onStop() {
        Log.d("MainActivity","OnStop of MainActivity");

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

        super.onStop();// ATTENTION: This was auto-generated to implement the App Indexing API.
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStart() {
        Log.d("MainActivity","OnStart of MainActivity");
        super.onStart();// ATTENTION: This was auto-generated to implement the App Indexing API.


        wifi_manager = (WifiManager) this.getSystemService(AttenuatorMainActivity.this.WIFI_SERVICE);
        WifiConfiguration wifi_configuration = null;
        if(wifi_manager.getWifiState() == 2 || wifi_manager.getWifiState() == 3) {// Enum Constantes for Wifi_enabling and Wifi_enabled
            Log.d("MainActivity","OnStart: Wifi was turned on @ onStart");
        }
        else {Log.d("MainActivity", "OnStart: Wifi was turned off @ onStart");}

        wifi_manager.setWifiEnabled(false);
        try {
            // Source http://stackoverflow.com/questions/13946607/android-how-to-turn-on-hotspot-in-android-programmatically
            Method method = wifi_manager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
            method.invoke(wifi_manager, wifi_configuration, true);
            Log.d("MainActivity","turned Hotspot on");
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }


        Thread t = new Thread() {
            @Override
            public void run() {
                Log.d("MainActivity", "Starting Thread, that updates SocketText");
                while (true) {
                    AttenuatorMainActivity.this.runOnUiThread(new Runnable() {
                        public void run() {
                            update_Sockettext();
                            //Log.d("MainActivity", "In While");
                        }
                    });

                    // run the sleep-Code NOT in UI-Thread! Will freeze the App.
                    try {
                        sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        t.start();
    }

}
