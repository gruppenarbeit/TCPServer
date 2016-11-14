package group_project.test001;;


/**
 * Created by andre_eggli on 11/14/16.
 */

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Random;

import static java.lang.Math.pow;
import static java.lang.System.out;

/**
 * Created by Markus on 11.11.2016.
 */

public class TCPServer_fakedata implements TCP_SERVER {

    private Thread serverThread = null;
    private WifiDataBuffer wifiDataBuffer;
    private ServerSocket serverSocket;
    private Socket socket;
    OutputStream outputStream;
    InputStream inputStream;
    final byte[] RD16 = "RD16".getBytes();
    final byte[] PEND = "PEND".getBytes();
    final byte[] TIME = "TIME".getBytes();
    final byte[] DRDY = "DRDY".getBytes();
    final byte[] DETV = "DETV".getBytes();
    final byte[] CALD = "CALD".getBytes();
    final byte[] SCAN = "SCAN".getBytes();

    final byte[] device_id = int2byteArray(125, 4);
    byte[] battery_charge = int2byteArray(77, 1); // in Prozent
    byte[] battery_voltage = int2byteArray(1677, 2); // in mV
    byte[] sequence_number = int2byteArray(1, 4); // TODO: Make dynamic
    byte[] RTC = {0, 0, 0, 0, 0, 0};
    final byte[] anzahl_tabellen= {8};
    final byte[] freqs_N = int2byteArray(100, 2);
    final byte[] levels_M = int2byteArray(16, 2);
    byte[] current_Pack = null;
    byte[] frequencies = reserviert(2, 0);
    byte[] LNA = {0};
    byte[] MODE = "P".getBytes(); // Peak, RMS, All

    public enum STATE {Start, Waiting, Time, Scan, Detv, Callibrate, Stop}

    public TCPServer_fakedata(final WifiDataBuffer wifiDataBuffer) throws IllegalStateException {
        Log.d("TCPServer","Constructor of TCPServer called");
        this.wifiDataBuffer = wifiDataBuffer;


        Thread.UncaughtExceptionHandler h = new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread th, Throwable ex) {
                Log.d("TCPServer", "UncaughtExceptionHandler rethrows IllegalStateException " + ex.getMessage());
                wifiDataBuffer.enque_FromESP(("ESP_ERROR: " + ex.getMessage()).getBytes());
                ex.printStackTrace();
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

                        for(STATE state = STATE.Start; state != STATE.Stop; state = getNextState(state)) {

                            Log.d("TCPServer", "Current State = "+ state.toString());

                            ByteArrayOutputStream DataFromESP = new ByteArrayOutputStream();

                            try {
                                switch (state) {
                                    case Start:
                                        sleep(2000); // 7 sec to turn on measurement device is fast
                                        DataFromESP.write(RD16);
                                        DataFromESP.write(DRDY);
                                        DataFromESP.write(device_id);
                                        DataFromESP.write(LNA);
                                        DataFromESP.write(reserviert(12, 0));
                                        DataFromESP.write(battery_charge);
                                        DataFromESP.write(battery_voltage);
                                        DataFromESP.write(PEND);
                                        send(DataFromESP.toByteArray());
                                        break;
                                    case Waiting:
                                        try {
                                            sleep(500);
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();
                                        }
                                        break;
                                    case Time:
                                        Log.d("TCPServer", "State Time not yet implemented :(");
                                        break;
                                    case Scan:
                                        Log.d("TCPServer", "Scaning @ " + ((Integer) byteArray2int(frequencies)).toString());
                                        DataFromESP.write(RD16);
                                        DataFromESP.write(SCAN);
                                        DataFromESP.write(device_id);
                                        DataFromESP.write(sequence_number);
                                        DataFromESP.write(RTC);
                                        DataFromESP.write(LNA);
                                        DataFromESP.write(frequencies);
                                        DataFromESP.write(raw_data());
                                        DataFromESP.write(reserviert(16, 0));
                                        DataFromESP.write(battery_charge);
                                        DataFromESP.write(battery_voltage);
                                        DataFromESP.write(PEND);
                                        send(DataFromESP.toByteArray());
                                        // sleep;
                                        break;
                                    case Callibrate:
                                        DataFromESP.write(RD16);
                                        DataFromESP.write(CALD);
                                        DataFromESP.write(device_id);
                                        DataFromESP.write(device_name());
                                        DataFromESP.write(RTC);
                                        DataFromESP.write(LNA);
                                        DataFromESP.write(anzahl_tabellen);
                                        DataFromESP.write(freqs_N);
                                        DataFromESP.write(levels_M);
                                        DataFromESP.write(Kalibrationstabelle());
                                        DataFromESP.write(battery_charge);
                                        DataFromESP.write(battery_voltage);
                                        DataFromESP.write("PEND".getBytes());
                                        send(DataFromESP.toByteArray());
                                        current_Pack = null;
                                        break;
                                    case Detv:
                                        Log.d("TCPServer", "State Detv not yet implemented :(");
                                        break;
                                    default:
                                        throw new IllegalStateException("Unknown State is" + state.toString());
                                }
                            } catch (IOException e){
                                e.printStackTrace();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }

                            try {
                                sleep(50);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }

                            if (wifiDataBuffer.isDataWaiting_ToESP()) { // send Trigger-Pack if one is available
                                byte[] received_from_Remo = wifiDataBuffer.dequeue_ToESP();
                                byte[] HeaderofTrigger = {received_from_Remo[4], received_from_Remo[5], received_from_Remo[6], received_from_Remo[7]};
                                Log.d("TCPServer","TCPServer_fake did receive a TriggerPackage of Type '"+ new String(HeaderofTrigger) +"' from Android");
                                current_Pack = received_from_Remo;
                                byte[] lna = {received_from_Remo[12]};
                                LNA = lna;
                            }
                        }
                    }
                }
            }
        };
        t.setUncaughtExceptionHandler(h);
        t.start();
    }

    private void send(byte[] DataPack) {
        if (checkForCorrectness(DataPack)) { // RD16, length, PEND
           // Log.d("TCPServer", "DataPack is correct! in send()");
        }
        byte[] HeaderofData = {DataPack[4], DataPack[5], DataPack[6], DataPack[7]};
        Log.d("TCPServer","TCPServer_fake did send a DataPackage of Type '"+ new String(HeaderofData) +"' to Android");
        wifiDataBuffer.enque_FromESP(DataPack);
    }

    private STATE getNextState(STATE old_state) {
        STATE new_state;
        byte[] mode;
        switch (old_state) {
            case Start:
                new_state = STATE.Waiting;
                break;
            default:
                if(current_Pack != null) {
                    byte[] header = split_packet(4, 7, current_Pack);
                    switch (new String(header)) {
                        case "CALD":
                            new_state = STATE.Callibrate;
                            break;
                        case "TIME":
                            new_state = STATE.Time;
                            frequencies = split_packet(13, 14, current_Pack);
                            MODE = new byte[]{current_Pack[15]};
                            if(byteArray2int(MODE) == 0) {
                                current_Pack = null;
                                new_state = STATE.Waiting;
                            }
                            break;
                        case "DETV":
                            new_state = STATE.Detv;
                            frequencies = split_packet(14, 13+2*current_Pack[13], current_Pack);
                            MODE = new byte[]{current_Pack[26]};
                            if(byteArray2int(MODE) == 0) {
                                current_Pack = null;
                                new_state = STATE.Waiting;
                            }
                            break;
                        case "SCAN":
                            new_state = STATE.Scan;
                            MODE = new byte[]{current_Pack[13]};
                            if(byteArray2int(MODE) == 0) {
                                current_Pack = null;
                                new_state = STATE.Waiting;
                                Log.d("TCPServer", "Received StopScanTrigger with MODE = " + (MODE[0] & 0xFF) + " - Going to STATE.Waiting");
                                break;
                            }
                            if(old_state == STATE.Scan) {

                                int currentscanfreq = byteArray2int(frequencies);
                                if (currentscanfreq >= 10000) {
                                    new_state =  STATE.Waiting;
                                    current_Pack = null;
                                } else {
                                    frequencies = int2byteArray(currentscanfreq + 100, 2);
                                }
                                break;
                            } else {
                                frequencies = int2byteArray(500, 2);
                            }
                            break;
                        default:
                            throw new IllegalStateException("Header " + new String(header) + " not valid in getNextState");
                    }
                }
                else {
                    new_state = STATE.Waiting; // For Example on Startup
                }
                break;
        }
        return new_state;
    }

    private byte[] int2byteArray (int Integr, int byteArray_length){
        byte[] byteArray = new byte[byteArray_length];
        if (Integr > pow((double) 2, (double) (8*byteArray_length)) - 1){
            Arrays.fill(byteArray,(byte) 0);
        }
        else {
            if (byteArray.length == 4){
                byteArray[0] = (byte)(Integr >> 24);
                byteArray[1] = (byte)(Integr >> 16);
                byteArray[2] = (byte)(Integr >> 8);
                byteArray[3] = (byte) Integr;
            }
            else if (byteArray.length == 2){
                byteArray[0] = (byte)(Integr >> 8);
                byteArray[1] = (byte)Integr;
            }
            // problems with wifiDataBuffer overflow: byteArray = ByteBuffer.allocate(byteArray_length).putInt(Integr).array();
        }
        return byteArray;
    }

    private int byteArray2int (byte[] byteArray){
        int Integr = 0;
        if (byteArray.length == 1) {
            Integr = byteArray[0] & 0xFF;
        }
        else if (byteArray.length == 2){
            Integr = (int) ((byteArray[0] & 0xFF) * pow(2, 8));
            Integr += byteArray[1] & 0xFF;
        }
        else if (byteArray.length == 4){
            Integr = (int) ((byteArray[0] & 0xFF) * pow(2, 24));
            Integr += (int) ((byteArray[1] & 0xFF) * pow(2, 16));
            Integr += (int) ((byteArray[2] & 0xFF) * pow(2, 8));
            Integr += byteArray[1] & 0xFF;
        }
        else {
            throw new IllegalArgumentException("byteArray must have length 1,2 or 3");
        }
        return Integr;
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

    private byte[] raw_data() {

        byte[] raw_data = new byte[8];
        //create a random byte
        Random rand = new Random();
        int random_number1 = rand.nextInt(125);
        int random_number2= rand.nextInt(125);
        byte random_byte1 = (byte) random_number1;
        byte random_byte2 = (byte) random_number2;
        String messgrösse_tostring = new String(MODE);
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
        else {
            throw new IllegalArgumentException("raw_data() not correctly implemented");
        }
        return raw_data;
    }

    private byte[] Kalibrationstabelle()
    {
        ByteArrayOutputStream inStreamBuffer = new ByteArrayOutputStream();
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
                byte[] temp= int2byteArray((i+1)*100, 2); // Frequenz-List
                inStreamBuffer.write(temp);
            }

            for(int cnt = 0; cnt < 16; ++cnt) {
                inStreamBuffer.write(int2byteArray(1000*cnt, 4));
            }


            for(int i=0; i<100; i++) //cal_data
            {
                for(int j=0; j<16; j++) //power_lvls
                {
                    inStreamBuffer.write(int2byteArray((j+1)*100, 4));//TODO: right range
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        byte[] result = inStreamBuffer.toByteArray();
        if (result.length != 6666) {
            throw  new IllegalStateException("Length of Tabelle is: " + ((Integer) result.length).toString());
        }
        return result;
    }

    private byte[] split_packet(int start, int end, byte[] packet) {
        int length = end - start + 1;
        byte[] splitted = new byte[length];
        for (int i = 0; i < length; i++) {
            splitted[i] = packet[i + start];
        }
        return splitted;
    }

    private boolean checkForCorrectness(byte[] received_from_ESP) {
        //check for RD16
        if (!new String(received_from_ESP).startsWith("RD16")) {
            throw new IllegalArgumentException("Header is not RD16 but: " + new String(split_packet(0, 3, received_from_ESP)));
        }
        //Check for PEND
        if (!new String(received_from_ESP).endsWith("PEND")) {
            throw new IllegalArgumentException("Packge doesnt end with PEND, but with: " + new String(received_from_ESP));
        }

        //Check if length is right for headerDetails
        byte[] header = split_packet(4, 7, received_from_ESP);
        switch (new String(header)) {
            case "DRDY":
                if (received_from_ESP.length != 32) {
                    throw new IllegalArgumentException("Header is not coherent with length");
                }
                return true;
            case "CALD":
                if (received_from_ESP.length != 53423) {
                    throw new IllegalArgumentException("Header is not coherent with length");
                }
                // Log.d("TCPServer","CALD-Pack ist of correct lenght = " + ((Integer)received_from_ESP.length).toString());
                return true;
            case "SCAN":
                if (received_from_ESP.length != 56) {
                    throw new IllegalArgumentException("Header is not coherent with length");
                }
                return true;
            default:
                throw new IllegalStateException("This kind of Heder is not known to ckeckfunction");

        }
    }
//        } else if ((new String(header).equals()) && !(received_from_ESP.length == 53423)) {
//            throw new IllegalArgumentException("Header is not coherent with length");
//        } else if ((new String(header).equals("SCAN")) && !(received_from_ESP.length == 56)) {
//            throw new IllegalArgumentException("Header is not coherent with length");
//        } else if ((new String(header).equals("DETV")) && !(received_from_ESP.length == 56)) {
//            throw new IllegalArgumentException("Header is not coherent with length");
//        } else if ((new String(header).equals("TIME")) && !(received_from_ESP.length == 95)) {
//            throw new IllegalArgumentException("Header is not coherent with length");
//        } else {
//            throw new IllegalArgumentException("Header not valid");
//        }
//        return true;


//    private byte[] fake_packet(byte[] received_from_Remo) {
//
//
//        ByteArrayOutputStream inStreamBuffer = new ByteArrayOutputStream(88);
//        try {
//
//
//            byte[] header = split_packet(0, 7, received_from_Remo);
//            String headerString = header.toString();
//            int PackSize = -1; // gives error if packsize is not overwriten
//            byte Atten_LNA = received_from_Remo[12]; //return Atten./LNA directly from received_from_Remo packet
//
//            //Todo
//            if (headerString.endsWith("CALD")) {
//                PackSize = 53423;
//                inStreamBuffer = new ByteArrayOutputStream(PackSize);
//                inStreamBuffer.write(header);
//                inStreamBuffer.write(device_id);
//                inStreamBuffer.write(device_name());
//                inStreamBuffer.write(RTC);
//                inStreamBuffer.write(Atten_LNA);
//                inStreamBuffer.write(anzahl_tabellen);
//                inStreamBuffer.write(freqs_N);
//                inStreamBuffer.write(levels_M);
//                inStreamBuffer.write(Kalibrationstabelle());
//                inStreamBuffer.write(battery_charge);
//                inStreamBuffer.write(battery_voltage);
//                inStreamBuffer.write("PEND".getBytes());
//            }
//            //done
//            else if (headerString.endsWith("SCAN")) {
//                PackSize = 56;
//                inStreamBuffer = new ByteArrayOutputStream(PackSize);
//                inStreamBuffer.write(header);
//                inStreamBuffer.write(device_id);
//                inStreamBuffer.write(sequence_number);
//                inStreamBuffer.write(RTC);
//                inStreamBuffer.write(Atten_LNA);
//                byte[] frequency = split_packet(13, 14, received_from_Remo);
//                inStreamBuffer.write(frequency);
//                byte[] raw_data = raw_data(); // number is for Messgrösse
//                inStreamBuffer.write(raw_data);
//                inStreamBuffer.write(reserviert(16, 0));
//                inStreamBuffer.write(battery_charge);
//                inStreamBuffer.write(battery_voltage);
//                inStreamBuffer.write("PEND".getBytes());
//
//            }
//            //done
//            else if (headerString.endsWith("DETV")) {
//                PackSize = 56;
//                int number_of_frequency = (int) received_from_Remo[13];
//                byte[] frequency = split_packet(14,25,received_from_Remo);
//                inStreamBuffer = new ByteArrayOutputStream(PackSize);
//                for(int i=0; i< number_of_frequency; i+=2) // two bytes per freq, therefore i+=2
//                {
//                    inStreamBuffer.write(header);
//                    inStreamBuffer.write(device_id);
//                    inStreamBuffer.write(sequence_number);
//                    inStreamBuffer.write(RTC);
//                    inStreamBuffer.write(Atten_LNA);
//                    inStreamBuffer.write(frequency[i]);
//                    inStreamBuffer.write(frequency[i+1]);
//                    byte[] raw_data = raw_data(received_from_Remo[26]); // number is for Messgrösse
//                    inStreamBuffer.write(raw_data);
//                    inStreamBuffer.write(reserviert(16, 0));
//                    inStreamBuffer.write(battery_charge);
//                    inStreamBuffer.write(battery_voltage);
//                    inStreamBuffer.write("PEND".getBytes());
//                }
//
//            }
//            //done
//            else if (headerString.endsWith("TIME")) {
//                PackSize = 95;
//                inStreamBuffer = new ByteArrayOutputStream(PackSize);
//                inStreamBuffer.write(header);
//                inStreamBuffer.write(device_id);
//                inStreamBuffer.write(sequence_number);
//                inStreamBuffer.write(RTC);
//                inStreamBuffer.write(Atten_LNA);
//                byte[] frequency = split_packet(13, 14, received_from_Remo);
//                inStreamBuffer.write(frequency);
//                byte[] raw_data = raw_data(received_from_Remo[15]); // number is for Messgrösse
//                inStreamBuffer.write(raw_data);
//                inStreamBuffer.write(reserviert(16, 0));
//                inStreamBuffer.write(reserviert(32, 0)); //GPS
//                inStreamBuffer.write(battery_charge);
//                inStreamBuffer.write(battery_voltage);
//                inStreamBuffer.write("PEND".getBytes());
//            }
//            //initialize inStreamBuffer even if there is an Error
//            else {
//                inStreamBuffer = new ByteArrayOutputStream(PackSize);
//                throw new IllegalArgumentException("This error occurs in fake_packet function");
//
//            }
//
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//        //returns one hole package with possibility to interrupt
//        return inStreamBuffer.toByteArray();
//    }


}

