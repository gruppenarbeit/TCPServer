package group_project.test001;


/**
 * Created by andre_eggli on 10/10/16.
 * Source: http://stackoverflow.com/questions/29991116/serversocket-accept-doesnt-works-in-second-time
 * Source: http://stackoverflow.com/questions/1212386/concurrent-and-blocking-queue-in-java
 */

import android.util.Log;
import org.apache.commons.io.IOUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.io.ByteArrayOutputStream;

public class TCPServer implements TCP_SERVER {

    private Thread serverThread = null;
    private WifiDataBuffer wifiDataBuffer;
    private ServerSocket serverSocket;
    private Socket socket;
    OutputStream outputStream;
    InputStream inputStream;
    Boolean ESPLostConnection = false;

    public synchronized void  forceStop() {
        this.ESPLostConnection = true;
    }

    public TCPServer(final WifiDataBuffer wifiDataBuffer) throws IllegalStateException {
        Log.d("TCPServer","Constructor of TCPServer called");
        this.wifiDataBuffer = wifiDataBuffer;

        Thread.UncaughtExceptionHandler h = new Thread.UncaughtExceptionHandler() {
            //Exceptions can occur after Consturctor is finished (but Thread still runns)
            public void uncaughtException(Thread th, Throwable ex) {
                Log.d("TCPServer","UncaughtExceptionHandler rethrows IllegalStateException "+ex.getMessage());
                ex.printStackTrace();
                SendErrorToActivity(1, "UncaughtException -> Please restart App");
            }
        };
        Thread mainThread = new Thread() {
            public void run() {

                socket = null;

                try {
                    Log.d("TCPServer", "Initialising a socket on port 8080, now waiting for ReadyPack from ESP...");
                    serverSocket = new ServerSocket(); // <-- create an unbound socket first
                    serverSocket.setReuseAddress(true);
                    serverSocket.bind(new InetSocketAddress(8080)); // <-- now bind it


                } catch (IOException e) {
                    e.printStackTrace();
                }

                try {
                    socket = serverSocket.accept();
                    outputStream = socket.getOutputStream();
                    inputStream = socket.getInputStream();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                while (!Thread.currentThread().isInterrupted() && !ESPLostConnection) {

                    try {
                        if (wifiDataBuffer.isDataWaiting_ToESP()) { // send Trigger-Pack if one is available
                            if (socket.getInetAddress().isReachable(100)) { // check if ESP still in reach
                                byte[] Triggerpackage2Send = wifiDataBuffer.dequeue_ToESP();
                                if (Triggerpackage2Send.length != 32) {
                                    throw new IllegalArgumentException("All TriggerPacks must have lenght 32!");
                                }
                                try {
                                    outputStream.write(Triggerpackage2Send); // send Trigger-Pack
                                } catch (SocketException exception) {
                                    exception.printStackTrace();
                                    // Log.d("TCPServer","ESP not reachable anymore");
                                    SendErrorToActivity(1, "No WiFi Connection. Please manually restart App");
                                    forceStop();
                                }
                                byte[] HeaderofTrigger = {Triggerpackage2Send[4], Triggerpackage2Send[5], Triggerpackage2Send[6], Triggerpackage2Send[7]};
                                Log.d("TCPServer", "TCPServer did send a TriggerPackage of Type '" + new String(HeaderofTrigger) + "' to ESP");
                            } else { // ESP not reachable
                                wifiDataBuffer.dequeue_ToESP();
                                Log.d("TCPServer", "ESP not reachable anymore");
                                SendErrorToActivity(1, "No WiFi Connection. Please manually restart App");
                                forceStop();
                            }
                        }

                        if (inputStream.available() >= 8) { // true if receiving Data.
                            int PackSize; // to be determined by HeaderDetails
                            Boolean receivePiecewise = false; // only for CAL-Pack

                            // IOUtils needs to be imported first
                            // Source: http://stackoverflow.com/questions/24578243/cannot-resolve-symbol-ioutils
                            // 1. File -> Project Structure... -> Dependencies
                            // 2. Click '+' in the upper right corner and select "Library dependency"
                            // 3. In the search field type: "org.apache.commons.io" and click Search
                            // 4. Select "org.apache.directory.studio:org.apache.commons.io:
                            byte[] header = IOUtils.toByteArray(inputStream, 8);
                            String headerAndDetails = new String(header);
                            if (!headerAndDetails.startsWith("RD16")) {
                                throw new IllegalArgumentException("Header is not RD16 but: " + headerAndDetails);
                            }
                            if (headerAndDetails.endsWith("DRDY")) {
                                PackSize = 32;
                            } else if (headerAndDetails.endsWith("CALD")) {
                                PackSize = 53423;
                                receivePiecewise = true;
                            } else if (headerAndDetails.endsWith("SCAN")) {
                                PackSize = 56;
                            } else if (headerAndDetails.endsWith("DETV")) {
                                PackSize = 56;
                            } else if (headerAndDetails.endsWith("TIME")) {
                                PackSize = 95;
                            } else {
                                throw new IllegalArgumentException("Detailheader is: " + headerAndDetails);
                            }

                            final ByteArrayOutputStream inStreamBuffer = new ByteArrayOutputStream(PackSize);
                            inStreamBuffer.write(header); // make a temporary Buffer to store whole incomming Package untill fully received

                            if (receivePiecewise) {
                                byte[] RD16 = "RD16".getBytes();
                                byte[] PROG = "PROG".getBytes();
                                byte[] PEND = "PEND".getBytes();
                                byte[] zero = {0};
                                int rec = 0;
                                while (rec < PackSize - 8) {
                                    ByteArrayOutputStream buffi = new ByteArrayOutputStream(14);
                                    buffi.write(RD16);
                                    buffi.write(PROG);
                                    buffi.write(zero);
                                    rec = inputStream.available();
                                    Integer progressInPercent = (int) (100.0 * rec / (PackSize - 8));
                                    byte[] percent = {progressInPercent.byteValue()};
                                    buffi.write(percent);
                                    buffi.write(PEND);
                                    wifiDataBuffer.enque_FromESP(buffi.toByteArray());
                                    sleep(500);
                                }
                                ByteArrayOutputStream End_ProgressPack = new ByteArrayOutputStream(14);
                                End_ProgressPack.write(RD16);
                                End_ProgressPack.write(PROG);
                                End_ProgressPack.write(((Integer) 100).byteValue());
                                End_ProgressPack.write(PEND);
                                wifiDataBuffer.enque_FromESP(End_ProgressPack.toByteArray());

                            }

                            byte[] content = IOUtils.toByteArray(inputStream, PackSize - 8); // read content of Package to corresponing header.
                            // IOUtils.toByteArray(inputStream, PackSize - 8) freezes until PackSize - 8 Bytes are avialable
                            String ContentString = new String(content); // Check if Postfix = "PEND"
                            if (!ContentString.endsWith("PEND")) {
                                throw new IllegalStateException("Packge doesnt end with PEND, but is: " + ContentString);
                            }
                            inStreamBuffer.write(content); // now inStreamBuffer contains Header AND content
                            wifiDataBuffer.enque_FromESP(inStreamBuffer.toByteArray()); // enqueue received Data into WifiDataBuffer;
                            Log.d("TCPServer", "Received a Package of Type " + headerAndDetails);
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }


                    // Make 100ms break if queue TO_ESP empty and no date available to receive
                    try {
                        if ((inputStream.available() < 8) && !wifiDataBuffer.isDataWaiting_ToESP()) {
                            try {
                                serverThread.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                try { // Thread has been interrupted or ESPlostConnection == true
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        };
        mainThread.setUncaughtExceptionHandler(h); // Because exceptions from Service are not passed to activity -> use 'SendErrorToAvtivity'
        mainThread.start();
    }  // end Constructor



    public void SendErrorToActivity (Integer Code, String Message){
        ByteArrayOutputStream errorbuffer = new ByteArrayOutputStream(134);
        try {
            errorbuffer.write("RD16EROR".getBytes());
            errorbuffer.write(new byte[]{0, Code.byteValue()});
            errorbuffer.write(Message.getBytes());
            for (int i = 0; i < 120-Message.length(); ++i){
                errorbuffer.write(((Integer) 0).byteValue());
            }
            errorbuffer.write("PEND".getBytes());
            wifiDataBuffer.enque_FromESP(errorbuffer.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}