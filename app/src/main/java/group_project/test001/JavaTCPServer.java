package group_project.test001;

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;


/**
 * Created by andre_eggli on 10/1/16.
 * Source: http://stackoverflow.com/questions/29991116/serversocket-accept-doesnt-works-in-second-time
 * Source: http://stackoverflow.com/questions/1212386/concurrent-and-blocking-queue-in-java
 */

public class JavaTCPServer {

    private Thread serverThread = null;
    private WifiDataBuffer wifiDataBuffer;
    MainActivity mainActivity;

    public boolean Callipack_Ready;
    public byte[] Callipack;


    public JavaTCPServer(WifiDataBuffer wifiDataBuffer, MainActivity mainActivity) {
        // TODO: mainActrivity only needed for Debugging.
        this.serverThread = new Thread(new ServerThread());
        this.serverThread.start();
        this.wifiDataBuffer = wifiDataBuffer;
        this.mainActivity = mainActivity;
        this.Callipack_Ready = false;
    }

    class ServerThread implements Runnable {

        private ServerSocket serverSocket;
        private Socket socket;

        OutputStream outputStream;
        InputStream inputStream;


        public void run() {
            socket = null;
            try {
                serverSocket = new ServerSocket(); // <-- create an unbound socket first
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(8080)); // <-- now bind it


            } catch (IOException e) {
                e.printStackTrace();

            }
            while (!Thread.currentThread().isInterrupted()) { // TODO: Richtige Abbruchbed
                try {
                    socket = serverSocket.accept();

                    outputStream = socket.getOutputStream();
                    inputStream = socket.getInputStream();
                } catch (IOException e) {
                    e.printStackTrace();
                }


                while (!Thread.currentThread().isInterrupted()) { // TODO: Richtige Abbruchbed.

//                  // TODO: Check if ESP still connected to Socket

                    try {

                        if (wifiDataBuffer.isDataWaiting_ToESP()){ // Send first to react faster on userinput
                            outputStream.write(wifiDataBuffer.dequeue_ToESP().getRowData());
                        }


                        if (inputStream.available() >= 8){
                            int PackSize; // to be determined by HeaderDetails


                            // IOUtils needs to be imported first
                            // Source: http://stackoverflow.com/questions/24578243/cannot-resolve-symbol-ioutils
                            // 1. File -> Project Structure... -> Dependencies
                            // 2. Click '+' in the upper right corner and select "Library dependency"
                            // 3. In the search field type: "org.apache.commons.io" and click Search
                            // 4. Select "org.apache.directory.studio:org.apache.commons.io:
                            byte[] header = IOUtils.toByteArray(inputStream, 8);
                            String headerAndDetails = new String(header);
                            if(!headerAndDetails.startsWith("RD16")){
                                throw new IllegalArgumentException("Header is not RD16 but: " + headerAndDetails);
                            }
                            if (headerAndDetails.endsWith("DRDY")) {
                                PackSize = 32;
                            }
                            else if (headerAndDetails.endsWith("CALD")) {
                                PackSize = 53423;
                            }
                            else if (headerAndDetails.endsWith("SCAN")) {
                                PackSize = 56;
                            }
                            else if (headerAndDetails.endsWith("DETV")) {
                                PackSize = 56;
                            }
                            else if (headerAndDetails.endsWith("TIME")) {
                                PackSize = 95;
                            }
                            else {
                                throw new IllegalArgumentException("Detailheader is: " + headerAndDetails);
                            }

                            final ByteArrayOutputStream inStreamBuffer = new ByteArrayOutputStream(PackSize);
                            inStreamBuffer.write(header);

                            // TODO: CALD-Pack is to big to be read in one go :(
                            // Workaround: read CALD-Pack in smaler Pieces
//                            while (PackSize > 2048) {
//                                PackSize -= 2048;
//                                // TODO: enqueue in pieces but as a whole.
//                                //outputStream.write(IOUtils.toByteArray(inputStream, 2048));
//                                inStreamBuffer.write(IOUtils.toByteArray(inputStream, 2048));
//                                // TODO: This is only for debugging
////                                mainActivity.runOnUiThread(new Runnable() {
////                                    @Override
////                                    public void run() {
////                                        mainActivity.update_Sockettext();
////                                    }
////                                });
//                            }
//
//                            if(PackSize < 4) { // if < 4 there will not be a footer 'PEND'
//                                throw new IOException("Packsize is: " + PackSize);
//                            }
                            byte[] content = IOUtils.toByteArray(inputStream, PackSize - 8);
                            String ContentString = new String(content); // Check if Postfix = "PEND"
                            if(!ContentString.endsWith("PEND")){
                                throw new IllegalArgumentException("Packge doesnt end with PEND, but is: " + ContentString);
                            }
                            inStreamBuffer.write(content);

//                            // Case inStreamBuffer is the callipack.
//                            if (headerAndDetails.endsWith("CALD")) {
//                                // not possible to pass it to mainactivity :(
//                                // Callipack is to large
//                                Callipack = inStreamBuffer.toByteArray();
//                                Callipack_Ready = true; // set Callipack as attribute of class JavaTCPServer
//
//                                // Attempt to pass Callipack via wifiDataBufffer or as below fails.
//                                mainActivity.runOnUiThread(new Runnable() {
//                                    @Override
//                                    public void run() {
//                                        // fails: mainActivity.set_Sockettext(Callipack);
//                                        // fails: wifiDataBuffer.enqueue_FromESP(new WifiPackage(Callipack))
//                                        // okey:  mainActivity.set_Sockettext({Callipack[1], Callipack[2]});
//
//                                        byte[] bla = "Received Callipack".getBytes();
//                                        mainActivity.set_Sockettext(Callipack);
//                                    }
//                                });
//                            }
//                            else { // Case inStreamBuffer does not contain a callipack
//
//                            }
                            wifiDataBuffer.enque_FromESP(new WifiPackage(inStreamBuffer.toByteArray())); // enqueue


                            // TODO: This is only for debugging
                            mainActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mainActivity.update_Sockettext();
                                }
                            });

                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }


                    // Make 100ms break if queue TO_ESP empty and no date available to receive
                    try {
                        if(!(inputStream.available() > 8) && !wifiDataBuffer.isDataWaiting_ToESP()) {
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
                // TODO: socket.close();
            }
        }
    }
}
