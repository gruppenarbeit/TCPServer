package group_project.test001;;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;

/**
 * Created by Markus on 11.11.2016.
 */

public class TCPServer_fakedata {

    private Thread serverThread = null;
    private WifiDataBuffer wifiDataBuffer;
    private ServerSocket serverSocket;
    private Socket socket;
    OutputStream outputStream;
    InputStream inputStream;
    byte[] device_id = {0, 0, 0, 125};
    byte[] battery_charge = {90}; // Prozent
    byte[] battery_voltage = {0, 10}; // = 10mV
    byte[] sequence_number = {77, 77, 77, 77}; // TODO: Make dynamic
    byte[] RTC = {0, 0, 0, 0, 0, 0};
    byte[] anzahl_tabellen= {8};
    byte[] freqs_N = {0, 100};
    byte[] levels_M = {0 ,16};
    STATE state;
    public enum STATE {Start, Stop}

    public TCPServer_fakedata(final WifiDataBuffer wifiDataBuffer) throws IllegalStateException {
        Log.d("TCPServer","Constructor of TCPServer called");
        this.wifiDataBuffer = wifiDataBuffer;
        state = STATE.Start;

        Thread.UncaughtExceptionHandler h = new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread th, Throwable ex) {
                Log.d("TCPServer", "UncaughtExceptionHandler rethrows IllegalStateException " + ex.getMessage());
                wifiDataBuffer.enque_FromESP(("ESP_ERROR: " + ex.getMessage()).getBytes());
            }
        };
        Thread t = new Thread() {
            public void run() {
                socket = null;
                try {
                    Log.d("TCPServer", "Initialising a socket on port 8080, now waiting for ReadyPack from ESP...");
                    // okey, socket is opend, but never used.
                    // but if these lines fail, realTCPServer will fail as well...
                    serverSocket = new ServerSocket(); // <-- create an unbound socket first
                    serverSocket.setReuseAddress(true);
                    serverSocket.bind(new InetSocketAddress(8080)); // <-- now bind it


                } catch (IOException e) {
                    e.printStackTrace();
                }
                while (!Thread.currentThread().isInterrupted()) {
                    //connect to ESP - serversocket.accept....


                    while (!Thread.currentThread().isInterrupted() ) {


                        // TODO: Check if ESP still connected to Socket

                        // try {
                            /* // this code would check very many times if socket is still reachable
                           if(!socket.getInetAddress().isReachable(100)){
                                throw new IllegalStateException("ESP not in reach");
                            }*/

                        if (wifiDataBuffer.isDataWaiting_ToESP()) { // send Trigger-Pack if one is available
                            //
                            byte[] received_from_Remo = wifiDataBuffer.dequeue_ToESP();
                            byte[] received_from_ESP = fake_packet(received_from_Remo);
                            check(received_from_ESP); // RD16, length, PEND
                            wifiDataBuffer.enque_FromESP(received_from_ESP);
                        }





 /*                          if (inputStream.available() >= 8)
                           { // true if receiving Data.
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
                                inStreamBuffer.write(header); // make a temporary Buffer to store whole incomming Package untill fully received


                                byte[] content = IOUtils.toByteArray(inputStream, PackSize - 8); // read content of Package to corresponing header.
                                String ContentString = new String(content); // Check if Postfix = "PEND"
                                if(!ContentString.endsWith("PEND")){
                                    throw new IllegalStateException("Packge doesnt end with PEND, but is: " + ContentString);
                                }
                                inStreamBuffer.write(content); // now inStreamBuffer contains Header AND content
                                wifiDataBuffer.enque_FromESP(inStreamBuffer.toByteArray()); // enqueue received Data into WifiDataBuffer
                            }

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
*/
                        // Make 100ms break if queue TO_ESP empty and no date available to receive
                        try {
                            if (!(inputStream.available() > 8) && !wifiDataBuffer.isDataWaiting_ToESP()) {
                                try {
                                    serverThread.sleep(100);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        try {
                            socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    }

                }
            }

        };
        t.setUncaughtExceptionHandler(h);
        t.start();
    }

    private byte[] device_name()
    {
        String device_name = "ESP_GEFAEHRLICHE_STRAHLUNG_MESSEGERAET";
        byte[] string_device_name = device_name.getBytes();
        byte[] get_zeros = reserviert(64 - string_device_name.length, 0);
        ByteArrayOutputStream inStreamBuffer = new ByteArrayOutputStream(64);
        try {
            inStreamBuffer.write(get_zeros);
            inStreamBuffer.write(string_device_name);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return inStreamBuffer.toByteArray();
    }

    private byte[] reserviert(int repeat, int number) {
        //fills array with size repeat with zeros
        final ByteArrayOutputStream inStreamBuffer = new ByteArrayOutputStream(repeat);
        try {
            byte[] temp = {((Integer) number).byteValue()};
            for (int i = 0; i < repeat; ++i) {
                inStreamBuffer.write(temp);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return inStreamBuffer.toByteArray();
    }

    private byte[] raw_data(byte Messgrösse) {

        byte[] raw_data = new byte[8];
        //create a random byte
        Random rand = new Random();
        int random_number1 = rand.nextInt(125);
        int random_number2= rand.nextInt(125);
        byte random_byte1 = (byte) random_number1;
        byte random_byte2 = (byte) random_number2;

        String messgrösse_tostring = Byte.toString(Messgrösse);
        if(messgrösse_tostring.equals("P"))
        {
            raw_data = new byte[]{0, 0, 0, 0, 0, 0, 0,random_byte1};
        }
        else if(messgrösse_tostring.equals("R"))
        {
            raw_data = new byte[]{0, 0, 0, random_byte1, 0, 0, 0, 0};
        }
        else if(messgrösse_tostring.equals("A"))
        {
            raw_data = new byte[]{0, 0, 0, random_byte1, 0, 0, 0, random_byte2};
        }
        return raw_data;
    }

    private byte[] Kalibrationstabelle(byte[] received_from_Remo)
    {
        ByteArrayOutputStream inStreamBuffer = new ByteArrayOutputStream(53328);
        String p = "P";
        byte[] Messgrösse_p = p.getBytes();
        for(int i=0; i< 4; i++) // 4times for P, einstellungen is  0,1,2,3
        {
            byte[] Einstellungen = {(byte) i};
            try {
                inStreamBuffer.write(tabelle(Messgrösse_p, Einstellungen)); //returns tabelle-array with 6666 bytes
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        String r = "R";
        byte[] Messgrösse_r = r.getBytes();
        for(int i=0; i< 4; i++) // 4times for R, einstellungen is  0,1,2,3
        {
            byte[] Einstellungen = {(byte) i};
            try {
                inStreamBuffer.write(tabelle(Messgrösse_r, Einstellungen)); //returns tabelle-array with 6666 bytes
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return inStreamBuffer.toByteArray();
    }

    //TODO: Fill with data
    private byte[] tabelle(byte[] Messgrösse, byte[] Einstellungen)
    {
        ByteArrayOutputStream inStreamBuffer = new ByteArrayOutputStream(6666);
        try {
            inStreamBuffer.write(Messgrösse);
            inStreamBuffer.write(Einstellungen);
            for(int i=0; i<100; i++) //freq_list
            {
                byte[] temp= { (byte) ((i+1)*100)}; // TODO: right range
                inStreamBuffer.write(temp);
            }
            for(int i=0; i<16; i++) //power_lvls
            {
                byte[] temp= {(byte) ((i+1)*100)}; //TODO: right range
                inStreamBuffer.write(temp);
            }
            for(int i=0; i<100; i++) //cal_data
            {
                //TODO: fill with data
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return inStreamBuffer.toByteArray();
    }

    private byte[] split_packet(int start, int end, byte[] packet) {
        int length = end - start + 1;
        byte[] splitted = new byte[length];
        for (int i = 0; i < length; i++) {
            splitted[i] = packet[i + start];
        }
        return splitted;
    }

    private boolean check(byte[] received_from_ESP) {
        //check for RD16
        if (!split_packet(0, 3, received_from_ESP).toString().equals("RD16")) {
            throw new IllegalArgumentException("Header is not RD16 but: " + split_packet(0, 3, received_from_ESP).toString());
        }
        //Check for PEND
        if (!received_from_ESP.toString().endsWith("PEND")) {
            throw new IllegalArgumentException("Packge doesnt end with PEND, but with: " + received_from_ESP.toString());
        }

        //Check if length is right for headerdeatails
        byte[] header = split_packet(3, 7, received_from_ESP);
        if ((header.toString().equals("DRDY")) && !(received_from_ESP.length == 32)) {
            throw new IllegalArgumentException("Header is not coherent with length");
        } else if ((header.toString().equals("CALD")) && !(received_from_ESP.length == 53423)) {
            throw new IllegalArgumentException("Header is not coherent with length");
        } else if ((header.toString().equals("SCAN")) && !(received_from_ESP.length == 56)) {
            throw new IllegalArgumentException("Header is not coherent with length");
        } else if ((header.toString().equals("DETV")) && !(received_from_ESP.length == 56)) {
            throw new IllegalArgumentException("Header is not coherent with length");
        } else if ((header.toString().equals("TIME")) && !(received_from_ESP.length == 95)) {
            throw new IllegalArgumentException("Header is not coherent with length");
        }
        return true;
    }

    private byte[] fake_packet(byte[] received_from_Remo) {


        ByteArrayOutputStream inStreamBuffer = new ByteArrayOutputStream(88);
        try {


            byte[] header = split_packet(0, 7, received_from_Remo);
            String headerString = header.toString();
            int PackSize = -1; // gives error if packsize is not overwriten
            byte Atten_LNA = received_from_Remo[12]; //return Atten./LNA directly from received_from_Remo packet


            //done
            if (headerString.endsWith("DRDY")) {
                PackSize = 32;
                inStreamBuffer = new ByteArrayOutputStream(PackSize);
                inStreamBuffer.write(header);
                inStreamBuffer.write(device_id);
                inStreamBuffer.write(Atten_LNA);
                inStreamBuffer.write(reserviert(12, 0));
                inStreamBuffer.write(battery_charge);
                inStreamBuffer.write(battery_voltage);
                inStreamBuffer.write("PEND".getBytes());
            }
            //Todo
            else if (headerString.endsWith("CALD")) {
                PackSize = 53423;
                inStreamBuffer = new ByteArrayOutputStream(PackSize);
                inStreamBuffer.write(header);
                inStreamBuffer.write(device_id);
                inStreamBuffer.write(device_name());
                inStreamBuffer.write(RTC);
                inStreamBuffer.write(Atten_LNA);
                inStreamBuffer.write(anzahl_tabellen);
                inStreamBuffer.write(freqs_N);
                inStreamBuffer.write(levels_M);
                inStreamBuffer.write(Kalibrationstabelle(received_from_Remo));
                inStreamBuffer.write(battery_charge);
                inStreamBuffer.write(battery_voltage);
                inStreamBuffer.write("PEND".getBytes());

            }
            //done
            else if (headerString.endsWith("SCAN")) {
                PackSize = 56;
                inStreamBuffer = new ByteArrayOutputStream(PackSize);
                inStreamBuffer.write(header);
                inStreamBuffer.write(device_id);
                inStreamBuffer.write(sequence_number);
                inStreamBuffer.write(RTC);
                inStreamBuffer.write(Atten_LNA);
                byte[] frequency = split_packet(13, 14, received_from_Remo);
                inStreamBuffer.write(frequency);
                byte[] raw_data = raw_data(received_from_Remo[13]); // number is for Messgrösse
                inStreamBuffer.write(raw_data);
                inStreamBuffer.write(reserviert(16, 0));
                inStreamBuffer.write(battery_charge);
                inStreamBuffer.write(battery_voltage);
                inStreamBuffer.write("PEND".getBytes());

            }
            //done
            else if (headerString.endsWith("DETV")) {
                PackSize = 56;
                int number_of_frequency = (int) received_from_Remo[13];
                byte[] frequency = split_packet(14,25,received_from_Remo);
                inStreamBuffer = new ByteArrayOutputStream(PackSize);
                for(int i=0; i< number_of_frequency; i+=2) // two bytes per freq, therefore i+=2
                {
                    inStreamBuffer.write(header);
                    inStreamBuffer.write(device_id);
                    inStreamBuffer.write(sequence_number);
                    inStreamBuffer.write(RTC);
                    inStreamBuffer.write(Atten_LNA);
                    inStreamBuffer.write(frequency[i]);
                    inStreamBuffer.write(frequency[i+1]);
                    byte[] raw_data = raw_data(received_from_Remo[26]); // number is for Messgrösse
                    inStreamBuffer.write(raw_data);
                    inStreamBuffer.write(reserviert(16, 0));
                    inStreamBuffer.write(battery_charge);
                    inStreamBuffer.write(battery_voltage);
                    inStreamBuffer.write("PEND".getBytes());
                }

            }
            //done
            else if (headerString.endsWith("TIME")) {
                PackSize = 95;
                inStreamBuffer = new ByteArrayOutputStream(PackSize);
                inStreamBuffer.write(header);
                inStreamBuffer.write(device_id);
                inStreamBuffer.write(sequence_number);
                inStreamBuffer.write(RTC);
                inStreamBuffer.write(Atten_LNA);
                byte[] frequency = split_packet(13, 14, received_from_Remo);
                inStreamBuffer.write(frequency);
                byte[] raw_data = raw_data(received_from_Remo[15]); // number is for Messgrösse
                inStreamBuffer.write(raw_data);
                inStreamBuffer.write(reserviert(16, 0));
                inStreamBuffer.write(reserviert(32, 0)); //GPS
                inStreamBuffer.write(battery_charge);
                inStreamBuffer.write(battery_voltage);
                inStreamBuffer.write("PEND".getBytes());
            }
            //initialize inStreamBuffer even if there is an Error
            else {
                inStreamBuffer = new ByteArrayOutputStream(PackSize);
                throw new IllegalArgumentException("This error occurs in fake_packet function");

            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        //returns one hole package with possibility to interrupt
        return inStreamBuffer.toByteArray();
    }


}
