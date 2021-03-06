package group_project.test001;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;

import static java.lang.Thread.sleep;

public class MainActivity extends AppCompatActivity {

    Button sendScan;
    Button SendLive;
    Button StopLive;
    Button SendCali;
    Button SendDETV;
    Button StopService;
    TextView SocketText;
    final String LOG_TAG = "MainActivity";
    WifiDataBuffer wifiDataBuffer = new WifiDataBuffer();
    BroadcastActivityReceiver broadcastActivityReceiver = new BroadcastActivityReceiver(LOG_TAG, wifiDataBuffer);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(LOG_TAG,"in OnCreate of MainActivity");
        super.onCreate(savedInstanceState);

        // Only Graphical Elements are Done @ onCreate
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        sendScan = (Button) findViewById(R.id.send_Scan);
        SendLive = (Button) findViewById(R.id.send_live);
        StopLive = (Button) findViewById(R.id.stop_live);
        SendCali = (Button) findViewById(R.id.send_calitrigger);
        SendDETV = (Button) findViewById(R.id.send_detail);
        StopService = (Button) findViewById(R.id.stop_service);
        SocketText = (TextView) findViewById(R.id.Sockettext);

        SendLive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SendLiveTrigger();
            }
        });
        sendScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SendScanTrigger();
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
        StopService.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                // TODO Auto-generated method stub
                StopService();
            }
        });
    }

    @Override
    public void onStart() {
        Log.d("MainActivity","OnStart of MainActivity");
        super.onStart();// ATTENTION: This was auto-generated to implement the App Indexing API.


        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(CommunicationService.TRIGGER_Serv2Act);
        registerReceiver(broadcastActivityReceiver, intentFilter);

        StartService();

        SendScanTrigger();

        Thread TextFieldUpdaterThread = new Thread() {
            @Override
            public void run() {
                Log.d("MainActivity", "Starting Thread, that updates SocketText");
                while (true) {
                    if(wifiDataBuffer.isDataWaiting_FromESP()){
                        MainActivity.this.runOnUiThread(new Runnable() {
                            public void run() {
                                update_Sockettext();
                                //Log.d("MainActivity", "In While");
                            }
                        });
                    } else{
                        // run the sleep-Code NOT in UI-Thread! Will freeze the App.
                        try {
                            sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        };
        TextFieldUpdaterThread.start();
    }

    @Override
    protected void onDestroy() {
        Log.d(LOG_TAG, "onDestroy()");
        if (this.isFinishing()) {
            StopService(); // turn Wifi Back on.
        }
        super.onDestroy();
    }

    @Override
    public void onStop() {
        Log.d(LOG_TAG,"OnStop");

        unregisterReceiver(broadcastActivityReceiver);

        super.onStop();
    }

    private void StopService() {
        Log.d(LOG_TAG, "StopService called");
        Intent i = new Intent(this, CommunicationService.class);
        stopService(i);

//        Intent intent = new Intent();
//        intent.setAction(CommunicationService.ACTION_FROM_ACTIVITY);
//        intent.putExtra(CommunicationService.COMMAND_Act2Serv,
//                CommunicationService.CMD_STOP);
    }

    private void StartService() {
        Intent intent = new Intent(this, CommunicationService.class);
        startService(intent);
    }
    private void sendTrigger(byte[] TriggerPack) {
        Intent intent = new Intent();
        intent.setAction(CommunicationService.ACTION_FROM_ACTIVITY);
        intent.putExtra(CommunicationService.TRIGGER_Act2Serv, TriggerPack);
        sendBroadcast(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }


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
            byte[] Header = {rowData[4], rowData[5], rowData[6], rowData[7]};
            String messageType = "Header: " + new String(Header) + "\n";
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
        sendTrigger(ScanTriggerPack);
    }

    private void SendLiveTrigger() {
        // byte[] LiveTriggerPack = {82,   68,   49,   54,   84,   73,   77,   69,    0,    0, 0, 125,    0,    0,    7,   65,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,   80,   69,   78,   68};
        byte[] LiveTriggerPack =  {82, 68, 49, 54, 84, 73, 77, 69, 0, 0, 0, 125, 0, 2, 88, 65, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 80, 69, 78, 68};
        sendTrigger(LiveTriggerPack);
    }

    private void SendLiveStop() {
        byte[] LiveStopPack = {82, 68, 49, 54, 84, 73, 77, 69, 0, 0, 0, 125, 0, 0, 7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 80, 69, 78, 68};
        sendTrigger(LiveStopPack);
    }

    private void SendCaliTrigger() {
        byte[] calitrigger = {82, 68, 49, 54, 67, 65, 76, 68,0, 0, 0, 125, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 80, 69, 78, 68};
        sendTrigger(calitrigger);
    }

    private void SendDETVTrigger() {
        byte[] DETVTrigger = {82, 68, 49, 54, 68, 69, 84, 86, 0, 0, 0, 125, 0, 2, 2, 88, 3, 32, 0, 0, 0, 0, 0, 0, 0, 0, 80, 0, 80, 69, 78, 68};
        //wifiDataBuffer.enqueue_ToESP(DETVTrigger);
        sendTrigger(DETVTrigger);
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

}