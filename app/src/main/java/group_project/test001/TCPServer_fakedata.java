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
import static java.lang.Thread.sleep;

/**
 * Created by Markus on 11.11.2016.
 */

public class TCPServer_fakedata implements TCP_SERVER {

    private WifiDataBuffer wifiDataBuffer;
    private ServerSocket serverSocket;
    private Socket socket;
    // OutputStream outputStream; // Not used in Facke-TCP-Server
    // InputStream inputStream; // Not used in Facke-TCP-Server
    final String LOG_TAG = "FAKE-TCPServer";

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
    int seq_nbr = 1;

    final byte[] anzahl_tabellen= {8};
    final byte[] freqs_N = int2byteArray(100, 2);
    final byte[] levels_M = int2byteArray(16, 2);
    byte[] current_Pack = null; // it is null if no unprocessed TriggerPackages are around.
    byte[] frequencies = reserviert(2, 0);
    byte[] LNA = {0};
    byte[] MODE = "P".getBytes(); // Peak, RMS, All

    Random rand = new Random();
    final int TimeItTakesToMeasureRMS = 200; // milliseconds, used in measre()
    final int TimeItTakesToMeasurePeak = 20; // milliseconds, used in measre()

    public enum STATE {Start, Waiting, Time, Scan, Detv, Callibrate, Stop}

    public TCPServer_fakedata(final WifiDataBuffer wifiDataBuffer) throws IllegalStateException {
        Log.d(LOG_TAG,"Constructor of TCPServer called");
        this.wifiDataBuffer = wifiDataBuffer;


        Thread.UncaughtExceptionHandler h = new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread th, Throwable ex) {
                Log.d(LOG_TAG, "UncaughtExceptionHandler rethrows IllegalStateException " + ex.getMessage());
                wifiDataBuffer.enque_FromESP(("ESP_ERROR: " + ex.getMessage()).getBytes());
                ex.printStackTrace();
            }
        };
        Thread t = new Thread() {
            public void run() {
                socket = null;
                try {
                    Log.d(LOG_TAG, "Initialising a socket on port 8080, now waiting for ReadyPack from ESP...");
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

                            ByteArrayOutputStream DataFromESP = new ByteArrayOutputStream();
                            try {
                                switch (state) {
                                    case Start:
                                        Log.d(LOG_TAG, "Current State = "+ state.toString());
                                        sleep(2000); // 2 sec to turn on measurement device is fast
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
                                        DataFromESP.write(RD16);
                                        DataFromESP.write(TIME);
                                        DataFromESP.write(device_id);
                                        DataFromESP.write(getSeqeneceNbr());
                                        DataFromESP.write(getRTC());
                                        DataFromESP.write(LNA);
                                        DataFromESP.write(frequencies);
                                        DataFromESP.write(measure(byteArray2int(frequencies)));
                                        DataFromESP.write(reserviert(16, 0));
                                        DataFromESP.write(reserviert(39, 0));
                                        DataFromESP.write(battery_charge);
                                        DataFromESP.write(battery_voltage);
                                        DataFromESP.write(PEND);
                                        send(DataFromESP.toByteArray());
                                        break;
                                    case Scan:
                                        DataFromESP.write(RD16);
                                        DataFromESP.write(SCAN);
                                        DataFromESP.write(device_id);
                                        DataFromESP.write(getSeqeneceNbr());
                                        DataFromESP.write(getRTC());
                                        DataFromESP.write(LNA);
                                        DataFromESP.write(frequencies);
                                        DataFromESP.write(measure(byteArray2int(frequencies)));
                                        DataFromESP.write(reserviert(16, 0));
                                        DataFromESP.write(battery_charge);
                                        DataFromESP.write(battery_voltage);
                                        DataFromESP.write(PEND);
                                        send(DataFromESP.toByteArray());
                                        break;
                                    case Callibrate:
                                        DataFromESP.write(RD16);
                                        DataFromESP.write(CALD);
                                        DataFromESP.write(device_id);
                                        DataFromESP.write(device_name());
                                        DataFromESP.write(getRTC());
                                        DataFromESP.write(LNA);
                                        DataFromESP.write(anzahl_tabellen);
                                        DataFromESP.write(freqs_N);
                                        DataFromESP.write(levels_M);
                                        DataFromESP.write(Kalibrationstabelle());
                                        DataFromESP.write(battery_charge);
                                        DataFromESP.write(battery_voltage);
                                        DataFromESP.write("PEND".getBytes());
                                        sleep(5000); // takes approx 30 sec in realiy
                                        send(DataFromESP.toByteArray());
                                        current_Pack = null;
                                        break;
                                    case Detv:
                                        for(int i = 0; i < frequencies.length/2; ++i) {
                                            DataFromESP.write(RD16);
                                            DataFromESP.write(DETV);
                                            DataFromESP.write(device_id);
                                            DataFromESP.write(getSeqeneceNbr());
                                            DataFromESP.write(getRTC());
                                            DataFromESP.write(LNA);
                                            DataFromESP.write(split_packet(2*i, 2*i + 1, frequencies));
                                            DataFromESP.write(measure(byteArray2int(split_packet(2*i, 2*i + 1, frequencies))));
                                            DataFromESP.write(reserviert(16, 0));
                                            DataFromESP.write(battery_charge);
                                            DataFromESP.write(battery_voltage);
                                            DataFromESP.write(PEND);
                                            send(DataFromESP.toByteArray());
                                            DataFromESP.reset();
                                        }
                                        break;
                                    default:
                                        throw new IllegalStateException("Unknown State is" + state.toString());
                                }
                            } catch (IOException e){
                                e.printStackTrace();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }


//                            // sleeping outsourced to measure()
//                            try {
//                                sleep(200);
//                            } catch (InterruptedException e) {
//                                e.printStackTrace();
//                            }

                            if (wifiDataBuffer.isDataWaiting_ToESP()) { // send Trigger-Pack if one is available
                                byte[] received_from_Remo = wifiDataBuffer.dequeue_ToESP();
                                byte[] HeaderofTrigger = {received_from_Remo[4], received_from_Remo[5], received_from_Remo[6], received_from_Remo[7]};
                                Log.d(LOG_TAG,"TCPServer_fake did receive a TriggerPackage of Type '"+ new String(HeaderofTrigger) +"' from Android");
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
        Log.d(LOG_TAG,"TCPServer_fake did send a DataPackage of Type '"+ new String(HeaderofData) +"' to Android");
        wifiDataBuffer.enque_FromESP(DataPack);
        incrementSeqenceNbr();
    }

    private STATE getNextState(STATE old_state) {
        STATE new_state;
        if (old_state == STATE.Start) {
            Log.d(LOG_TAG, "Goid to STATE.Waiting");
            new_state = STATE.Waiting;
        } else {
            if (current_Pack != null) {
                byte[] header = split_packet(4, 7, current_Pack);
                switch (new String(header)) {
                    case "CALD":
                        new_state = STATE.Callibrate;
                        Log.d(LOG_TAG, "Starting Sending Callibration-Package. This takes 7 seconds. Data is not yet from Marcos Excel-File");
                        break;
                    case "TIME":
                        new_state = STATE.Time;
                        frequencies = split_packet(13, 14, current_Pack);
                        MODE = new byte[]{current_Pack[15]};
                        if (byteArray2int(MODE) == 0) {
                            current_Pack = null;
                            new_state = STATE.Waiting;
                            Log.d(LOG_TAG, "End Live because received a TIME-Stop-Package, going to wait");
                        }
                        break;
                    case "DETV":
                        new_state = STATE.Detv;
                        frequencies = split_packet(14, 13 + 2 * current_Pack[13], current_Pack);
                        MODE = new byte[]{current_Pack[26]};
                        if (byteArray2int(MODE) == 0) {
                            current_Pack = null;
                            new_state = STATE.Waiting;
                            Log.d(LOG_TAG, "End Scan because received a DETV-Stop-Package, going to wait");
                        }
                        break;
                    case "SCAN":
                        new_state = STATE.Scan;
                        MODE = new byte[]{current_Pack[13]};
                        if (byteArray2int(MODE) == 0) {
                            current_Pack = null;
                            new_state = STATE.Waiting;
                            Log.d(LOG_TAG, "Received StopScanTrigger with MODE = '" + (MODE[0] & 0xFF) + "', going to wait");
                            break;
                        }
                        if (old_state == STATE.Scan) {

                            int currentscanfreq = byteArray2int(frequencies);
                            if (currentscanfreq >= 10000) {
                                new_state = STATE.Waiting;
                                Log.d(LOG_TAG, "Scan finished, going to wait");
                                current_Pack = null;
                            } else {
                                frequencies = int2byteArray(currentscanfreq + 100, 2);
                            }
                            break;
                        } else {
                            frequencies = int2byteArray(500, 2);
                            Log.d(LOG_TAG, "Starting Scan");
                        }
                        break;
                    default:
                        throw new IllegalStateException("Header " + new String(header) + " not valid in getNextState");
                }
            } else {
                new_state = STATE.Waiting; // For Example on Startup
            }
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

    private byte[] getSeqeneceNbr() {
        return int2byteArray(seq_nbr, 4);
    }

    private boolean incrementSeqenceNbr() {
        seq_nbr++;
        return true;
    }

    private byte[] getRTC() {
        return new byte[]{0, 0, 0, 0, 0, 0}; // measure...
    }

    private byte[] device_name()
    {
        String device_name = "I am so Fake";
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

    private byte[] measure(int freqency) {
        // TODO: create more realistic measurement Data.

        ByteArrayOutputStream result = new ByteArrayOutputStream(8);
        byte[] zeros = int2byteArray(0, 4);

        String messgrösse_tostring = new String(MODE);
        try{

            if(messgrösse_tostring.equals("P")) {
                sleep(TimeItTakesToMeasurePeak); // emulate measurement-behavior

                result.write(zeros);

                // TODO: make better
                int meas = rand.nextInt(1000);
                result.write(int2byteArray(meas, 4));

                switch (byteArray2int(LNA)){

                    case 0:

                        break;
                    case 1:
                        break;
                    case 2:
                        break;
                    case 3:
                        break;
                    default:
                        throw new IllegalStateException("LNA out of range");
                }
            } else if(messgrösse_tostring.equals("R")) {
                sleep(TimeItTakesToMeasureRMS);

                // TODO: make better
                int meas = rand.nextInt(1000);
                result.write(int2byteArray(meas, 4));
                result.write(zeros);

                switch (byteArray2int(LNA)){

                    case 0:
                        break;
                    case 1:
                        break;
                    case 2:
                        break;
                    case 3:
                        break;
                    default:
                        throw new IllegalStateException("LNA out of range");
                }

            } else if (messgrösse_tostring.equals("A")) {
                sleep(TimeItTakesToMeasureRMS + TimeItTakesToMeasurePeak);

                // TODO: make better
                int meas = rand.nextInt(1000);
                result.write(int2byteArray(meas, 4));
                // TODO: make better
                meas = rand.nextInt(1000);
                result.write(int2byteArray(meas, 4));

                switch (byteArray2int(LNA)){

                    case 0:

                        break;
                    case 1:
                        break;
                    case 2:
                        break;
                    case 3:
                        break;
                    default:
                        throw new IllegalStateException("LNA out of range");
                }
            } else {
                throw new IllegalArgumentException("raw_data() not correctly implemented");
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return result.toByteArray();
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
                    throw new IllegalArgumentException("Header is not coherent with length. length = " + ((Integer) received_from_ESP.length).toString());
                }
                return true;
            case "CALD":
                if (received_from_ESP.length != 53423) {
                    throw new IllegalArgumentException("Header is not coherent with length. length = " + ((Integer) received_from_ESP.length).toString());
                }
                // Log.d("TCPServer","CALD-Pack ist of correct lenght = " + ((Integer)received_from_ESP.length).toString());
                return true;
            case "SCAN":
                if (received_from_ESP.length != 56) {
                    throw new IllegalArgumentException("Header is not coherent with length. length = " + ((Integer) received_from_ESP.length).toString());
                }
                return true;
            case "TIME":
                if (received_from_ESP.length != 95) {
                    throw new IllegalArgumentException("Header is not coherent with length. length = " + ((Integer) received_from_ESP.length).toString());
                }
                return true;
            case "DETV":
                if (received_from_ESP.length != 56) {
                    throw new IllegalArgumentException("Header is not coherent with length. length = " + ((Integer) received_from_ESP.length).toString());
                }
                return true;
            default:
                throw new IllegalStateException("Header "+ new String(split_packet(4, 7, received_from_ESP)) +" is not known to ckeckfunction");

        }
    }
}

