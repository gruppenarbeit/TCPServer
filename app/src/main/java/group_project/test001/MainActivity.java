package group_project.test001;


import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;
import java.lang.reflect.Method;

public class MainActivity extends AppCompatActivity {

    GoogleApiClient client;
    Button Update_Connection;
    Button sendScan;
    Button SendLive;
    Button StopLive;
    Button SendCali;
    Button SendDETV;
    TextView SocketText;
    WifiManager wifi_manager;
    JavaTCPServer Socket;
    WifiDataBuffer wifiDataBuffer; // Via WifiDataBufffer messages are being passed between Threads to JavaTCPServer to ESP

    // Dequeues an element from wifiDataBuffer and puts it on a TextView
    public void update_Sockettext() { // TODO: Dequeueing elements schould be done by Matthias Thread
        if(wifiDataBuffer.isDataWaiting_FromESP()){
            byte[] rowData = wifiDataBuffer.deque_FromESP();
            int maxleng = rowData.length;
            String str = "Messages received:\n";
            if(maxleng > 200) {
                maxleng = 200;
            }

            String messageType = "\nWifiPackage.toString: ";
            for (int i = 0; i < maxleng; i++) {
                messageType += ( rowData[i] & 0xFF)  + "#";
            }
            messageType += ",\n";

            SocketText.setText(str + messageType);
        }
        else {
            SocketText.setText("Queue empty");
        }
    }

//    public void set_Sockettext( byte[]  callipack) {
//        String messageType = "\nByte[] Callipack: ";
//        if(callipack.length <= 200){
//            for (byte b: callipack) {
//                messageType += ( b & 0xFF)  + "#";
//            }
//            messageType += ",\n";
//            SocketText.setText(messageType);
//        }
//        else {
//            for (int i = 0; i < 100; i++) {
//                messageType += ( callipack[i] & 0xFF)  + "#";
//            }
//            messageType += ",\n";
//            SocketText.setText(messageType);
//        }
//
//    }

    // For sending Commands to the ESP.
    private void SendScanTrigger() { // TODO: This schould be done by Matthias Thread
        byte[] ScanTriggerPack = {82, 68,   49,   54,   83,   67,   65,   78,   0,   8,   1,   5,   0,   80,   50,   50,   50,   50,   50,   50,   50,    50,   50,  50,   50,   50,   50,   50,   80,   69,   78,   68};
        wifiDataBuffer.enqueue_ToESP(ScanTriggerPack);
    }

    private void SendLiveTrigger () {
        byte[] LiveTriggerPack = {82,   68,   49,   54,   84,   73,   77,   69,    0,    8,    1,    5,    0,    0,    7,   65,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,   80,   69,   78,   68};
        wifiDataBuffer.enqueue_ToESP(LiveTriggerPack);
    }

    private  void SendLiveStop () {
        byte[] LiveStopPack = {82,   68,   49,   54,   84,   73,   77,   69,    0,    8,    1,    5,    0,    0,    7,   0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,   80,   69,   78,   68};
        wifiDataBuffer.enqueue_ToESP(LiveStopPack);
    }

    private void SendCaliTrigger () {
        byte[] calitrigger = {82, 68, 49, 54, 67, 65, 76, 68, 0, 8, 1, 5, 0,  0, 0,     0,     0,     0,     0,     0,     0,     0,     0,     0,     0,     0,    0,     0,    80,   69,   78,   68};
        wifiDataBuffer.enqueue_ToESP(calitrigger);
    }

    private void SendDETVTrigger () {
        byte[] DETVTrigger = {82, 68, 49, 54, 68, 69, 84, 86, 0, 8, 1, 5, 0,  4, 0,     2,     0,     20,     0,     60,     0,     100,     0,     0,     0,     0,    80,     0,    80,   69,   78,   68};
        wifiDataBuffer.enqueue_ToESP(DETVTrigger);
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Update_Connection = (Button) findViewById(R.id.Update_Connection);
        sendScan = (Button) findViewById(R.id.send_Scan);
        SendLive = (Button) findViewById(R.id.send_live);
        StopLive = (Button) findViewById(R.id.stop_live);
        SendCali = (Button) findViewById(R.id.send_calitrigger);
        SendDETV = (Button) findViewById(R.id.send_detail);

        wifi_manager = (WifiManager) this.getSystemService(MainActivity.this.WIFI_SERVICE);
        WifiConfiguration wifi_configuration = null;
        wifi_manager.setWifiEnabled(false);
        try
        {
            // Source http://stackoverflow.com/questions/13946607/android-how-to-turn-on-hotspot-in-android-programmatically
            Method method=wifi_manager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
            method.invoke(wifi_manager, wifi_configuration, true);
        }
        catch (Exception e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
//        apManager = new ApManager();
        // Set Wifi in AP-Mode on Startup:
  //      ApManager.turnHotspotOn(MainActivity.this);

        wifiDataBuffer = new WifiDataBuffer();
        Socket = new JavaTCPServer(wifiDataBuffer, this); // Initialise JavaTCPServer as well
        SocketText = (TextView) findViewById(R.id.Sockettext);

        // Update should be made by seperate thread
        Update_Connection.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                update_Sockettext(); // What has been received from ESP so far
            }
        });

        sendScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SendScanTrigger();
            }
        });

        SendLive.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                SendLiveTrigger();
            }
        });

        StopLive.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v){
                SendLiveStop();
            }
        });

        SendCali.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v){
                SendCaliTrigger();
            }
        });

        SendDETV.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                SendDETVTrigger();
            }
        });



// **********************************************************************************
//
// Vom Code hier unten hat André keine Ahnung.
//
// **********************************************************************************

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
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
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app URL is correct.
                Uri.parse("android-app://group_project.test001/http/host/path")
        );
        AppIndex.AppIndexApi.start(client, viewAction);
    }

    @Override
    public void onStop() {
        super.onStop();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app URL is correct.
                Uri.parse("android-app://group_project.test001/http/host/path")
        );
        AppIndex.AppIndexApi.end(client, viewAction);
        client.disconnect();
    }
}
