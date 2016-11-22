package group_project.test001;


/**
 * Created by andre_eggli on 11/14/16.
 */

import android.util.Log;

import com.example.matthustahli.radarexposimeter.WifiDataBuffer;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import static java.lang.Math.pow;
import static java.lang.Thread.sleep;

/**
 * Created by Markus on 11.11.2016.
 */

public class TCPServer_fakedata /*implements TCP_SERVER*/ {

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
    private int cald_progress = 0;

    public enum STATE {Start, Waiting, Time, Scan, Detv, Callibrate, Stop}

    public synchronized int get_Progress(){
        return cald_progress;
    }
    public synchronized void increment_Progress() {
        cald_progress += 1;
    }

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
                                        for(int i = 0; i < 27; ++i){
                                            sleep(500);
                                            increment_Progress();
                                        }
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

    // TODO: create more realistic measurement Data.
    private byte[] measure(int freqency) {


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
            byte[] LNA_Settings = {(byte) i};
            try {
                inStreamBuffer.write(tabelle(Messgrösse_p, LNA_Settings, p, i)); //returns tabelle-array with 6666 bytes
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
                inStreamBuffer.write(tabelle(Messgrösse_r, Einstellungen, r, i)); //returns tabelle-array with 6666 bytes
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return inStreamBuffer.toByteArray();
    }


    private byte[] tabelle(byte[] Peak_RMS_All, byte[] LNA_Settings, String Peak_RMS, int LNA)

    {
        ByteArrayOutputStream inStreamBuffer = new ByteArrayOutputStream(6666);

        try {
            inStreamBuffer.write(Peak_RMS_All);
            inStreamBuffer.write(LNA_Settings);

            int[] csvString = data(Peak_RMS,LNA); // returns 1-dim int[] filled with all data from one csv-datasheet
            int temp=0;

            //iterate first through freq, then power, then data
            //take each required int out-->temp, write temp then it into byte[] with right size

            for(int i=1; i<101; i++) { // Frequenz-List as int16 = 2 Bytes
                temp = csvString[i*17];
                inStreamBuffer.write(int2byteArray(temp,2));
            }

            for(int j = 1; j < 17; ++j) { // Power levels as int32 = 4 Bytes
                temp = csvString[j];
                inStreamBuffer.write(int2byteArray(temp,4));
            }

            for(int i=1; i<101; i++) // loop through cal_data
            {
                for(int j=1; j<16; j++) // loop through  power_levels
                {
                    temp = csvString[(i*17) + j];
                    inStreamBuffer.write(int2byteArray(temp,4));
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

    private int[] data(String Peak_RMS,int LNA){
        int[] P0= {89,158,281,500,889,1581,2812,5000,8891,15811,28117,50000,88914,158114,281171,500000,
                500,482,569,699,829,960,1090,1220,1351,1481,1611,1742,1872,2002,2133,2263,2393,
                550,487,574,704,834,965,1095,1225,1356,1486,1616,1747,1877,2007,2138,2268,2398,
                600,492,579,709,839,970,1100,1230,1361,1491,1621,1752,1882,2012,2143,2273,2403,
                650,497,584,714,844,975,1105,1235,1366,1496,1626,1757,1887,2017,2148,2278,2408,
                700,502,589,719,849,980,1110,1240,1371,1501,1631,1762,1892,2022,2153,2283,2413,
                750,507,594,724,854,985,1115,1245,1376,1506,1636,1767,1897,2027,2158,2288,2418,
                800,512,599,729,859,990,1120,1250,1381,1511,1641,1772,1902,2032,2163,2293,2423,
                850,517,604,734,864,995,1125,1255,1386,1516,1646,1777,1907,2037,2168,2298,2428,
                900,522,609,739,869,1000,1130,1260,1391,1521,1651,1782,1912,2042,2173,2303,2433,
                1000,533,620,750,880,1011,1141,1271,1402,1532,1662,1793,1923,2053,2184,2314,2444,
                1100,544,631,761,891,1022,1152,1282,1413,1543,1673,1804,1934,2064,2195,2325,2455,
                1200,555,642,772,902,1033,1163,1293,1424,1554,1684,1815,1945,2075,2206,2336,2466,
                1300,566,653,783,913,1044,1174,1304,1435,1565,1695,1826,1956,2086,2217,2347,2477,
                1400,577,664,794,924,1055,1185,1315,1446,1576,1706,1837,1967,2097,2228,2358,2488,
                1500,588,675,805,935,1066,1196,1326,1457,1587,1717,1848,1978,2108,2239,2369,2499,
                1600,599,686,816,946,1077,1207,1337,1468,1598,1728,1859,1989,2119,2250,2380,2510,
                1700,610,697,827,957,1088,1218,1348,1479,1609,1739,1870,2000,2130,2261,2391,2521,
                1800,621,708,838,968,1099,1229,1359,1490,1620,1750,1881,2011,2141,2272,2402,2532,
                1900,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                2000,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                2100,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                2200,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                2300,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                2400,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                2500,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                2600,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                2700,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                2800,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                2900,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                3000,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                3100,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                3200,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                3300,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                3400,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                3500,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                3600,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                3700,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                3800,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                3900,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                4000,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                4100,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                4200,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                4300,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                4400,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                4500,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                4600,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                4700,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                4800,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                4900,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                5000,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                5100,629,716,846,976,1107,1237,1367,1498,1628,1758,1889,2019,2149,2280,2410,2540,
                5200,626,713,843,973,1104,1234,1364,1495,1625,1755,1886,2016,2146,2277,2407,2537,
                5300,623,710,840,970,1101,1231,1361,1492,1622,1752,1883,2013,2143,2274,2404,2534,
                5400,620,707,837,967,1098,1228,1358,1489,1619,1749,1880,2010,2140,2271,2401,2531,
                5500,617,704,834,964,1095,1225,1355,1486,1616,1746,1877,2007,2137,2268,2398,2528,
                5600,614,701,831,961,1092,1222,1352,1483,1613,1743,1874,2004,2134,2265,2395,2525,
                5700,611,698,828,958,1089,1219,1349,1480,1610,1740,1871,2001,2131,2262,2392,2522,
                5800,608,695,825,955,1086,1216,1346,1477,1607,1737,1868,1998,2128,2259,2389,2519,
                5900,605,692,822,952,1083,1213,1343,1474,1604,1734,1865,1995,2125,2256,2386,2516,
                6000,602,689,819,949,1080,1210,1340,1471,1601,1731,1862,1992,2122,2253,2383,2513,
                6100,599,686,816,946,1077,1207,1337,1468,1598,1728,1859,1989,2119,2250,2380,2510,
                6200,596,683,813,943,1074,1204,1334,1465,1595,1725,1856,1986,2116,2247,2377,2507,
                6300,593,680,810,940,1071,1201,1331,1462,1592,1722,1853,1983,2113,2244,2374,2504,
                6400,590,677,807,937,1068,1198,1328,1459,1589,1719,1850,1980,2110,2241,2371,2501,
                6500,587,674,804,934,1065,1195,1325,1456,1586,1716,1847,1977,2107,2238,2368,2498,
                6600,584,671,801,931,1062,1192,1322,1453,1583,1713,1844,1974,2104,2235,2365,2495,
                6700,581,668,798,928,1059,1189,1319,1450,1580,1710,1841,1971,2101,2232,2362,2492,
                6800,578,665,795,925,1056,1186,1316,1447,1577,1707,1838,1968,2098,2229,2359,2489,
                6900,575,662,792,922,1053,1183,1313,1444,1574,1704,1835,1965,2095,2226,2356,2486,
                7000,572,659,789,919,1050,1180,1310,1441,1571,1701,1832,1962,2092,2223,2353,2483,
                7100,569,656,786,916,1047,1177,1307,1438,1568,1698,1829,1959,2089,2220,2350,2480,
                7200,566,653,783,913,1044,1174,1304,1435,1565,1695,1826,1956,2086,2217,2347,2477,
                7300,563,650,780,910,1041,1171,1301,1432,1562,1692,1823,1953,2083,2214,2344,2474,
                7400,560,647,777,907,1038,1168,1298,1429,1559,1689,1820,1950,2080,2211,2341,2471,
                7500,557,644,774,904,1035,1165,1295,1426,1556,1686,1817,1947,2077,2208,2338,2468,
                7600,554,641,771,901,1032,1162,1292,1423,1553,1683,1814,1944,2074,2205,2335,2465,
                7700,551,638,768,898,1029,1159,1289,1420,1550,1680,1811,1941,2071,2202,2332,2462,
                7800,548,635,765,895,1026,1156,1286,1417,1547,1677,1808,1938,2068,2199,2329,2459,
                7900,545,632,762,892,1023,1153,1283,1414,1544,1674,1805,1935,2065,2196,2326,2456,
                8000,474,629,759,889,1020,1150,1280,1411,1541,1671,1802,1932,2062,2193,2323,2453,
                8100,474,627,757,887,1018,1148,1278,1409,1539,1669,1800,1930,2060,2191,2321,2451,
                8200,474,625,755,885,1016,1146,1276,1407,1537,1667,1798,1928,2058,2189,2319,2449,
                8300,474,623,753,883,1014,1144,1274,1405,1535,1665,1796,1926,2056,2187,2317,2447,
                8400,474,621,751,881,1012,1142,1272,1403,1533,1663,1794,1924,2054,2185,2315,2445,
                8500,474,611,741,871,1002,1132,1262,1393,1523,1653,1784,1914,2044,2175,2305,2435,
                8600,474,601,731,861,992,1122,1252,1383,1513,1643,1774,1904,2034,2165,2295,2425,
                8700,474,591,721,851,982,1112,1242,1373,1503,1633,1764,1894,2024,2155,2285,2415,
                8800,474,581,711,841,972,1102,1232,1363,1493,1623,1754,1884,2014,2145,2275,2405,
                8900,474,571,701,831,962,1092,1222,1353,1483,1613,1744,1874,2004,2135,2265,2395,
                9000,474,561,691,821,952,1082,1212,1343,1473,1603,1734,1864,1994,2125,2255,2385,
                9100,474,551,681,811,942,1072,1202,1333,1463,1593,1724,1854,1984,2115,2245,2375,
                9200,474,541,671,801,932,1062,1192,1323,1453,1583,1714,1844,1974,2105,2235,2365,
                9300,474,531,661,791,922,1052,1182,1313,1443,1573,1704,1834,1964,2095,2225,2355,
                9400,474,474,651,781,912,1042,1172,1303,1433,1563,1694,1824,1954,2085,2215,2345,
                9500,474,474,641,771,902,1032,1162,1293,1423,1553,1684,1814,1944,2075,2205,2335,
                9600,474,474,631,761,892,1022,1152,1283,1413,1543,1674,1804,1934,2065,2195,2325,
                9700,474,474,621,751,882,1012,1142,1273,1403,1533,1664,1794,1924,2055,2185,2315,
                9800,474,474,611,741,872,1002,1132,1263,1393,1523,1654,1784,1914,2045,2175,2305,
                9900,474,474,601,731,862,992,1122,1253,1383,1513,1644,1774,1904,2035,2165,2295,
                10000,474,474,591,721,852,982,1112,1243,1373,1503,1634,1764,1894,2025,2155,2285};

        int[] P21 = {89,158,281,500,889,1581,2812,5000,8891,15811,28117,50000,88914,158114,281171,500000,
                500,482,569,699,829,960,1090,1220,1351,1481,1611,1742,1872,2002,2133,2263,2393,
                550,487,574,704,834,965,1095,1225,1356,1486,1616,1747,1877,2007,2138,2268,2398,
                600,492,579,709,839,970,1100,1230,1361,1491,1621,1752,1882,2012,2143,2273,2403,
                650,497,584,714,844,975,1105,1235,1366,1496,1626,1757,1887,2017,2148,2278,2408,
                700,502,589,719,849,980,1110,1240,1371,1501,1631,1762,1892,2022,2153,2283,2413,
                750,507,594,724,854,985,1115,1245,1376,1506,1636,1767,1897,2027,2158,2288,2418,
                800,512,599,729,859,990,1120,1250,1381,1511,1641,1772,1902,2032,2163,2293,2423,
                850,517,604,734,864,995,1125,1255,1386,1516,1646,1777,1907,2037,2168,2298,2428,
                900,522,609,739,869,1000,1130,1260,1391,1521,1651,1782,1912,2042,2173,2303,2433,
                1000,533,620,750,880,1011,1141,1271,1402,1532,1662,1793,1923,2053,2184,2314,2444,
                1100,544,631,761,891,1022,1152,1282,1413,1543,1673,1804,1934,2064,2195,2325,2455,
                1200,555,642,772,902,1033,1163,1293,1424,1554,1684,1815,1945,2075,2206,2336,2466,
                1300,566,653,783,913,1044,1174,1304,1435,1565,1695,1826,1956,2086,2217,2347,2477,
                1400,577,664,794,924,1055,1185,1315,1446,1576,1706,1837,1967,2097,2228,2358,2488,
                1500,588,675,805,935,1066,1196,1326,1457,1587,1717,1848,1978,2108,2239,2369,2499,
                1600,599,686,816,946,1077,1207,1337,1468,1598,1728,1859,1989,2119,2250,2380,2510,
                1700,610,697,827,957,1088,1218,1348,1479,1609,1739,1870,2000,2130,2261,2391,2521,
                1800,621,708,838,968,1099,1229,1359,1490,1620,1750,1881,2011,2141,2272,2402,2532,
                1900,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                2000,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                2100,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                2200,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                2300,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                2400,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                2500,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                2600,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                2700,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                2800,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                2900,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                3000,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                3100,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                3200,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                3300,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                3400,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                3500,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                3600,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                3700,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                3800,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                3900,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                4000,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                4100,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                4200,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                4300,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                4400,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                4500,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                4600,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                4700,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                4800,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                4900,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                5000,632,719,849,979,1110,1240,1370,1501,1631,1761,1892,2022,2152,2283,2413,2543,
                5100,629,716,846,976,1107,1237,1367,1498,1628,1758,1889,2019,2149,2280,2410,2540,
                5200,626,713,843,973,1104,1234,1364,1495,1625,1755,1886,2016,2146,2277,2407,2537,
                5300,623,710,840,970,1101,1231,1361,1492,1622,1752,1883,2013,2143,2274,2404,2534,
                5400,620,707,837,967,1098,1228,1358,1489,1619,1749,1880,2010,2140,2271,2401,2531,
                5500,617,704,834,964,1095,1225,1355,1486,1616,1746,1877,2007,2137,2268,2398,2528,
                5600,614,701,831,961,1092,1222,1352,1483,1613,1743,1874,2004,2134,2265,2395,2525,
                5700,611,698,828,958,1089,1219,1349,1480,1610,1740,1871,2001,2131,2262,2392,2522,
                5800,608,695,825,955,1086,1216,1346,1477,1607,1737,1868,1998,2128,2259,2389,2519,
                5900,605,692,822,952,1083,1213,1343,1474,1604,1734,1865,1995,2125,2256,2386,2516,
                6000,602,689,819,949,1080,1210,1340,1471,1601,1731,1862,1992,2122,2253,2383,2513,
                6100,599,686,816,946,1077,1207,1337,1468,1598,1728,1859,1989,2119,2250,2380,2510,
                6200,596,683,813,943,1074,1204,1334,1465,1595,1725,1856,1986,2116,2247,2377,2507,
                6300,593,680,810,940,1071,1201,1331,1462,1592,1722,1853,1983,2113,2244,2374,2504,
                6400,590,677,807,937,1068,1198,1328,1459,1589,1719,1850,1980,2110,2241,2371,2501,
                6500,587,674,804,934,1065,1195,1325,1456,1586,1716,1847,1977,2107,2238,2368,2498,
                6600,584,671,801,931,1062,1192,1322,1453,1583,1713,1844,1974,2104,2235,2365,2495,
                6700,581,668,798,928,1059,1189,1319,1450,1580,1710,1841,1971,2101,2232,2362,2492,
                6800,578,665,795,925,1056,1186,1316,1447,1577,1707,1838,1968,2098,2229,2359,2489,
                6900,575,662,792,922,1053,1183,1313,1444,1574,1704,1835,1965,2095,2226,2356,2486,
                7000,572,659,789,919,1050,1180,1310,1441,1571,1701,1832,1962,2092,2223,2353,2483,
                7100,569,656,786,916,1047,1177,1307,1438,1568,1698,1829,1959,2089,2220,2350,2480,
                7200,566,653,783,913,1044,1174,1304,1435,1565,1695,1826,1956,2086,2217,2347,2477,
                7300,563,650,780,910,1041,1171,1301,1432,1562,1692,1823,1953,2083,2214,2344,2474,
                7400,560,647,777,907,1038,1168,1298,1429,1559,1689,1820,1950,2080,2211,2341,2471,
                7500,557,644,774,904,1035,1165,1295,1426,1556,1686,1817,1947,2077,2208,2338,2468,
                7600,554,641,771,901,1032,1162,1292,1423,1553,1683,1814,1944,2074,2205,2335,2465,
                7700,551,638,768,898,1029,1159,1289,1420,1550,1680,1811,1941,2071,2202,2332,2462,
                7800,548,635,765,895,1026,1156,1286,1417,1547,1677,1808,1938,2068,2199,2329,2459,
                7900,545,632,762,892,1023,1153,1283,1414,1544,1674,1805,1935,2065,2196,2326,2456,
                8000,474,629,759,889,1020,1150,1280,1411,1541,1671,1802,1932,2062,2193,2323,2453,
                8100,474,627,757,887,1018,1148,1278,1409,1539,1669,1800,1930,2060,2191,2321,2451,
                8200,474,625,755,885,1016,1146,1276,1407,1537,1667,1798,1928,2058,2189,2319,2449,
                8300,474,623,753,883,1014,1144,1274,1405,1535,1665,1796,1926,2056,2187,2317,2447,
                8400,474,621,751,881,1012,1142,1272,1403,1533,1663,1794,1924,2054,2185,2315,2445,
                8500,474,611,741,871,1002,1132,1262,1393,1523,1653,1784,1914,2044,2175,2305,2435,
                8600,474,601,731,861,992,1122,1252,1383,1513,1643,1774,1904,2034,2165,2295,2425,
                8700,474,591,721,851,982,1112,1242,1373,1503,1633,1764,1894,2024,2155,2285,2415,
                8800,474,581,711,841,972,1102,1232,1363,1493,1623,1754,1884,2014,2145,2275,2405,
                8900,474,571,701,831,962,1092,1222,1353,1483,1613,1744,1874,2004,2135,2265,2395,
                9000,474,561,691,821,952,1082,1212,1343,1473,1603,1734,1864,1994,2125,2255,2385,
                9100,474,551,681,811,942,1072,1202,1333,1463,1593,1724,1854,1984,2115,2245,2375,
                9200,474,541,671,801,932,1062,1192,1323,1453,1583,1714,1844,1974,2105,2235,2365,
                9300,474,531,661,791,922,1052,1182,1313,1443,1573,1704,1834,1964,2095,2225,2355,
                9400,474,474,651,781,912,1042,1172,1303,1433,1563,1694,1824,1954,2085,2215,2345,
                9500,474,474,641,771,902,1032,1162,1293,1423,1553,1684,1814,1944,2075,2205,2335,
                9600,474,474,631,761,892,1022,1152,1283,1413,1543,1674,1804,1934,2065,2195,2325,
                9700,474,474,621,751,882,1012,1142,1273,1403,1533,1664,1794,1924,2055,2185,2315,
                9800,474,474,611,741,872,1002,1132,1263,1393,1523,1654,1784,1914,2045,2175,2305,
                9900,474,474,601,731,862,992,1122,1253,1383,1513,1644,1774,1904,2035,2165,2295,
                10000,474,474,591,721,852,982,1112,1243,1373,1503,1634,1764,1894,2025,2155,2285};

        int[] P42 = {889,1581,2812,5000,8891,15811,28117,50000,88914,158114,281171,500000,889140,1581139,2811707,5000000,
                500,457,544,674,804,935,1065,1195,1326,1456,1586,1717,1847,1977,2108,2238,2368,
                550,462,549,679,809,940,1070,1200,1331,1461,1591,1722,1852,1982,2113,2243,2373,
                600,467,554,684,814,945,1075,1205,1336,1466,1596,1727,1857,1987,2118,2248,2378,
                650,472,559,689,819,950,1080,1210,1341,1471,1601,1732,1862,1992,2123,2253,2383,
                700,477,564,694,824,955,1085,1215,1346,1476,1606,1737,1867,1997,2128,2258,2388,
                750,482,569,699,829,960,1090,1220,1351,1481,1611,1742,1872,2002,2133,2263,2393,
                800,487,574,704,834,965,1095,1225,1356,1486,1616,1747,1877,2007,2138,2268,2398,
                850,492,579,709,839,970,1100,1230,1361,1491,1621,1752,1882,2012,2143,2273,2403,
                900,497,584,714,844,975,1105,1235,1366,1496,1626,1757,1887,2017,2148,2278,2408,
                1000,508,595,725,855,986,1116,1246,1377,1507,1637,1768,1898,2028,2159,2289,2419,
                1100,519,606,736,866,997,1127,1257,1388,1518,1648,1779,1909,2039,2170,2300,2430,
                1200,530,617,747,877,1008,1138,1268,1399,1529,1659,1790,1920,2050,2181,2311,2441,
                1300,541,628,758,888,1019,1149,1279,1410,1540,1670,1801,1931,2061,2192,2322,2452,
                1400,552,639,769,899,1030,1160,1290,1421,1551,1681,1812,1942,2072,2203,2333,2463,
                1500,563,650,780,910,1041,1171,1301,1432,1562,1692,1823,1953,2083,2214,2344,2474,
                1600,574,661,791,921,1052,1182,1312,1443,1573,1703,1834,1964,2094,2225,2355,2485,
                1700,585,672,802,932,1063,1193,1323,1454,1584,1714,1845,1975,2105,2236,2366,2496,
                1800,596,683,813,943,1074,1204,1334,1465,1595,1725,1856,1986,2116,2247,2377,2507,
                1900,607,694,824,954,1085,1215,1345,1476,1606,1736,1867,1997,2127,2258,2388,2518,
                2000,607,694,824,954,1085,1215,1345,1476,1606,1736,1867,1997,2127,2258,2388,2518,
                2100,607,694,824,954,1085,1215,1345,1476,1606,1736,1867,1997,2127,2258,2388,2518,
                2200,607,694,824,954,1085,1215,1345,1476,1606,1736,1867,1997,2127,2258,2388,2518,
                2300,607,694,824,954,1085,1215,1345,1476,1606,1736,1867,1997,2127,2258,2388,2518,
                2400,607,694,824,954,1085,1215,1345,1476,1606,1736,1867,1997,2127,2258,2388,2518,
                2500,607,694,824,954,1085,1215,1345,1476,1606,1736,1867,1997,2127,2258,2388,2518,
                2600,607,694,824,954,1085,1215,1345,1476,1606,1736,1867,1997,2127,2258,2388,2518,
                2700,607,694,824,954,1085,1215,1345,1476,1606,1736,1867,1997,2127,2258,2388,2518,
                2800,607,694,824,954,1085,1215,1345,1476,1606,1736,1867,1997,2127,2258,2388,2518,
                2900,607,694,824,954,1085,1215,1345,1476,1606,1736,1867,1997,2127,2258,2388,2518,
                3000,607,694,824,954,1085,1215,1345,1476,1606,1736,1867,1997,2127,2258,2388,2518,
                3100,607,694,824,954,1085,1215,1345,1476,1606,1736,1867,1997,2127,2258,2388,2518,
                3200,607,694,824,954,1085,1215,1345,1476,1606,1736,1867,1997,2127,2258,2388,2518,
                3300,607,694,824,954,1085,1215,1345,1476,1606,1736,1867,1997,2127,2258,2388,2518,
                3400,607,694,824,954,1085,1215,1345,1476,1606,1736,1867,1997,2127,2258,2388,2518,
                3500,607,694,824,954,1085,1215,1345,1476,1606,1736,1867,1997,2127,2258,2388,2518,
                3600,607,694,824,954,1085,1215,1345,1476,1606,1736,1867,1997,2127,2258,2388,2518,
                3700,607,694,824,954,1085,1215,1345,1476,1606,1736,1867,1997,2127,2258,2388,2518,
                3800,607,694,824,954,1085,1215,1345,1476,1606,1736,1867,1997,2127,2258,2388,2518,
                3900,607,694,824,954,1085,1215,1345,1476,1606,1736,1867,1997,2127,2258,2388,2518,
                4000,607,694,824,954,1085,1215,1345,1476,1606,1736,1867,1997,2127,2258,2388,2518,
                4100,607,694,824,954,1085,1215,1345,1476,1606,1736,1867,1997,2127,2258,2388,2518,
                4200,607,694,824,954,1085,1215,1345,1476,1606,1736,1867,1997,2127,2258,2388,2518,
                4300,607,694,824,954,1085,1215,1345,1476,1606,1736,1867,1997,2127,2258,2388,2518,
                4400,607,694,824,954,1085,1215,1345,1476,1606,1736,1867,1997,2127,2258,2388,2518,
                4500,607,694,824,954,1085,1215,1345,1476,1606,1736,1867,1997,2127,2258,2388,2518,
                4600,607,694,824,954,1085,1215,1345,1476,1606,1736,1867,1997,2127,2258,2388,2518,
                4700,607,694,824,954,1085,1215,1345,1476,1606,1736,1867,1997,2127,2258,2388,2518,
                4800,607,694,824,954,1085,1215,1345,1476,1606,1736,1867,1997,2127,2258,2388,2518,
                4900,607,694,824,954,1085,1215,1345,1476,1606,1736,1867,1997,2127,2258,2388,2518,
                5000,607,694,824,954,1085,1215,1345,1476,1606,1736,1867,1997,2127,2258,2388,2518,
                5100,604,691,821,951,1082,1212,1342,1473,1603,1733,1864,1994,2124,2255,2385,2515,
                5200,601,688,818,948,1079,1209,1339,1470,1600,1730,1861,1991,2121,2252,2382,2512,
                5300,598,685,815,945,1076,1206,1336,1467,1597,1727,1858,1988,2118,2249,2379,2509,
                5400,595,682,812,942,1073,1203,1333,1464,1594,1724,1855,1985,2115,2246,2376,2506,
                5500,592,679,809,939,1070,1200,1330,1461,1591,1721,1852,1982,2112,2243,2373,2503,
                5600,589,676,806,936,1067,1197,1327,1458,1588,1718,1849,1979,2109,2240,2370,2500,
                5700,586,673,803,933,1064,1194,1324,1455,1585,1715,1846,1976,2106,2237,2367,2497,
                5800,583,670,800,930,1061,1191,1321,1452,1582,1712,1843,1973,2103,2234,2364,2494,
                5900,580,667,797,927,1058,1188,1318,1449,1579,1709,1840,1970,2100,2231,2361,2491,
                6000,577,664,794,924,1055,1185,1315,1446,1576,1706,1837,1967,2097,2228,2358,2488,
                6100,574,661,791,921,1052,1182,1312,1443,1573,1703,1834,1964,2094,2225,2355,2485,
                6200,571,658,788,918,1049,1179,1309,1440,1570,1700,1831,1961,2091,2222,2352,2482,
                6300,568,655,785,915,1046,1176,1306,1437,1567,1697,1828,1958,2088,2219,2349,2479,
                6400,565,652,782,912,1043,1173,1303,1434,1564,1694,1825,1955,2085,2216,2346,2476,
                6500,562,649,779,909,1040,1170,1300,1431,1561,1691,1822,1952,2082,2213,2343,2473,
                6600,559,646,776,906,1037,1167,1297,1428,1558,1688,1819,1949,2079,2210,2340,2470,
                6700,556,643,773,903,1034,1164,1294,1425,1555,1685,1816,1946,2076,2207,2337,2467,
                6800,553,640,770,900,1031,1161,1291,1422,1552,1682,1813,1943,2073,2204,2334,2464,
                6900,550,637,767,897,1028,1158,1288,1419,1549,1679,1810,1940,2070,2201,2331,2461,
                7000,547,634,764,894,1025,1155,1285,1416,1546,1676,1807,1937,2067,2198,2328,2458,
                7100,544,631,761,891,1022,1152,1282,1413,1543,1673,1804,1934,2064,2195,2325,2455,
                7200,541,628,758,888,1019,1149,1279,1410,1540,1670,1801,1931,2061,2192,2322,2452,
                7300,538,625,755,885,1016,1146,1276,1407,1537,1667,1798,1928,2058,2189,2319,2449,
                7400,535,622,752,882,1013,1143,1273,1404,1534,1664,1795,1925,2055,2186,2316,2446,
                7500,532,619,749,879,1010,1140,1270,1401,1531,1661,1792,1922,2052,2183,2313,2443,
                7600,529,616,746,876,1007,1137,1267,1398,1528,1658,1789,1919,2049,2180,2310,2440,
                7700,526,613,743,873,1004,1134,1264,1395,1525,1655,1786,1916,2046,2177,2307,2437,
                7800,523,610,740,870,1001,1131,1261,1392,1522,1652,1783,1913,2043,2174,2304,2434,
                7900,520,607,737,867,998,1128,1258,1389,1519,1649,1780,1910,2040,2171,2301,2431,
                8000,449,604,734,864,995,1125,1255,1386,1516,1646,1777,1907,2037,2168,2298,2428,
                8100,449,602,732,862,993,1123,1253,1384,1514,1644,1775,1905,2035,2166,2296,2426,
                8200,449,600,730,860,991,1121,1251,1382,1512,1642,1773,1903,2033,2164,2294,2424,
                8300,449,598,728,858,989,1119,1249,1380,1510,1640,1771,1901,2031,2162,2292,2422,
                8400,449,596,726,856,987,1117,1247,1378,1508,1638,1769,1899,2029,2160,2290,2420,
                8500,449,586,716,846,977,1107,1237,1368,1498,1628,1759,1889,2019,2150,2280,2410,
                8600,449,576,706,836,967,1097,1227,1358,1488,1618,1749,1879,2009,2140,2270,2400,
                8700,449,566,696,826,957,1087,1217,1348,1478,1608,1739,1869,1999,2130,2260,2390,
                8800,449,556,686,816,947,1077,1207,1338,1468,1598,1729,1859,1989,2120,2250,2380,
                8900,449,546,676,806,937,1067,1197,1328,1458,1588,1719,1849,1979,2110,2240,2370,
                9000,449,536,666,796,927,1057,1187,1318,1448,1578,1709,1839,1969,2100,2230,2360,
                9100,449,526,656,786,917,1047,1177,1308,1438,1568,1699,1829,1959,2090,2220,2350,
                9200,449,516,646,776,907,1037,1167,1298,1428,1558,1689,1819,1949,2080,2210,2340,
                9300,449,506,636,766,897,1027,1157,1288,1418,1548,1679,1809,1939,2070,2200,2330,
                9400,449,449,626,756,887,1017,1147,1278,1408,1538,1669,1799,1929,2060,2190,2320,
                9500,449,449,616,746,877,1007,1137,1268,1398,1528,1659,1789,1919,2050,2180,2310,
                9600,449,449,606,736,867,997,1127,1258,1388,1518,1649,1779,1909,2040,2170,2300,
                9700,449,449,596,726,857,987,1117,1248,1378,1508,1639,1769,1899,2030,2160,2290,
                9800,449,449,586,716,847,977,1107,1238,1368,1498,1629,1759,1889,2020,2150,2280,
                9900,449,449,576,706,837,967,1097,1228,1358,1488,1619,1749,1879,2010,2140,2270,
                10000,449,449,566,696,827,957,1087,1218,1348,1478,1609,1739,1869,2000,2130,2260};

        int[] PLNA = {1,2,3,5,9,16,28,50,89,158,281,500,889,1581,2812,5000,
                500,520,607,737,867,998,1128,1258,1389,1519,1649,1780,1910,2040,2171,2301,2431,
                550,525,612,742,872,1003,1133,1263,1394,1524,1654,1785,1915,2045,2176,2306,2436,
                600,530,617,747,877,1008,1138,1268,1399,1529,1659,1790,1920,2050,2181,2311,2441,
                650,535,622,752,882,1013,1143,1273,1404,1534,1664,1795,1925,2055,2186,2316,2446,
                700,540,627,757,887,1018,1148,1278,1409,1539,1669,1800,1930,2060,2191,2321,2451,
                750,545,632,762,892,1023,1153,1283,1414,1544,1674,1805,1935,2065,2196,2326,2456,
                800,550,637,767,897,1028,1158,1288,1419,1549,1679,1810,1940,2070,2201,2331,2461,
                850,555,642,772,902,1033,1163,1293,1424,1554,1684,1815,1945,2075,2206,2336,2466,
                900,560,647,777,907,1038,1168,1298,1429,1559,1689,1820,1950,2080,2211,2341,2471,
                1000,571,658,788,918,1049,1179,1309,1440,1570,1700,1831,1961,2091,2222,2352,2482,
                1100,582,669,799,929,1060,1190,1320,1451,1581,1711,1842,1972,2102,2233,2363,2493,
                1200,593,680,810,940,1071,1201,1331,1462,1592,1722,1853,1983,2113,2244,2374,2504,
                1300,604,691,821,951,1082,1212,1342,1473,1603,1733,1864,1994,2124,2255,2385,2515,
                1400,615,702,832,962,1093,1223,1353,1484,1614,1744,1875,2005,2135,2266,2396,2526,
                1500,626,713,843,973,1104,1234,1364,1495,1625,1755,1886,2016,2146,2277,2407,2537,
                1600,637,724,854,984,1115,1245,1375,1506,1636,1766,1897,2027,2157,2288,2418,2548,
                1700,648,735,865,995,1126,1256,1386,1517,1647,1777,1908,2038,2168,2299,2429,2559,
                1800,659,746,876,1006,1137,1267,1397,1528,1658,1788,1919,2049,2179,2310,2440,2570,
                1900,670,757,887,1017,1148,1278,1408,1539,1669,1799,1930,2060,2190,2321,2451,2581,
                2000,670,757,887,1017,1148,1278,1408,1539,1669,1799,1930,2060,2190,2321,2451,2581,
                2100,670,757,887,1017,1148,1278,1408,1539,1669,1799,1930,2060,2190,2321,2451,2581,
                2200,670,757,887,1017,1148,1278,1408,1539,1669,1799,1930,2060,2190,2321,2451,2581,
                2300,670,757,887,1017,1148,1278,1408,1539,1669,1799,1930,2060,2190,2321,2451,2581,
                2400,670,757,887,1017,1148,1278,1408,1539,1669,1799,1930,2060,2190,2321,2451,2581,
                2500,670,757,887,1017,1148,1278,1408,1539,1669,1799,1930,2060,2190,2321,2451,2581,
                2600,670,757,887,1017,1148,1278,1408,1539,1669,1799,1930,2060,2190,2321,2451,2581,
                2700,670,757,887,1017,1148,1278,1408,1539,1669,1799,1930,2060,2190,2321,2451,2581,
                2800,670,757,887,1017,1148,1278,1408,1539,1669,1799,1930,2060,2190,2321,2451,2581,
                2900,670,757,887,1017,1148,1278,1408,1539,1669,1799,1930,2060,2190,2321,2451,2581,
                3000,670,757,887,1017,1148,1278,1408,1539,1669,1799,1930,2060,2190,2321,2451,2581,
                3100,670,757,887,1017,1148,1278,1408,1539,1669,1799,1930,2060,2190,2321,2451,2581,
                3200,670,757,887,1017,1148,1278,1408,1539,1669,1799,1930,2060,2190,2321,2451,2581,
                3300,670,757,887,1017,1148,1278,1408,1539,1669,1799,1930,2060,2190,2321,2451,2581,
                3400,670,757,887,1017,1148,1278,1408,1539,1669,1799,1930,2060,2190,2321,2451,2581,
                3500,670,757,887,1017,1148,1278,1408,1539,1669,1799,1930,2060,2190,2321,2451,2581,
                3600,670,757,887,1017,1148,1278,1408,1539,1669,1799,1930,2060,2190,2321,2451,2581,
                3700,670,757,887,1017,1148,1278,1408,1539,1669,1799,1930,2060,2190,2321,2451,2581,
                3800,670,757,887,1017,1148,1278,1408,1539,1669,1799,1930,2060,2190,2321,2451,2581,
                3900,670,757,887,1017,1148,1278,1408,1539,1669,1799,1930,2060,2190,2321,2451,2581,
                4000,670,757,887,1017,1148,1278,1408,1539,1669,1799,1930,2060,2190,2321,2451,2581,
                4100,670,757,887,1017,1148,1278,1408,1539,1669,1799,1930,2060,2190,2321,2451,2581,
                4200,670,757,887,1017,1148,1278,1408,1539,1669,1799,1930,2060,2190,2321,2451,2581,
                4300,670,757,887,1017,1148,1278,1408,1539,1669,1799,1930,2060,2190,2321,2451,2581,
                4400,670,757,887,1017,1148,1278,1408,1539,1669,1799,1930,2060,2190,2321,2451,2581,
                4500,670,757,887,1017,1148,1278,1408,1539,1669,1799,1930,2060,2190,2321,2451,2581,
                4600,670,757,887,1017,1148,1278,1408,1539,1669,1799,1930,2060,2190,2321,2451,2581,
                4700,670,757,887,1017,1148,1278,1408,1539,1669,1799,1930,2060,2190,2321,2451,2581,
                4800,670,757,887,1017,1148,1278,1408,1539,1669,1799,1930,2060,2190,2321,2451,2581,
                4900,670,757,887,1017,1148,1278,1408,1539,1669,1799,1930,2060,2190,2321,2451,2581,
                5000,670,757,887,1017,1148,1278,1408,1539,1669,1799,1930,2060,2190,2321,2451,2581,
                5100,667,754,884,1014,1145,1275,1405,1536,1666,1796,1927,2057,2187,2318,2448,2578,
                5200,664,751,881,1011,1142,1272,1402,1533,1663,1793,1924,2054,2184,2315,2445,2575,
                5300,661,748,878,1008,1139,1269,1399,1530,1660,1790,1921,2051,2181,2312,2442,2572,
                5400,658,745,875,1005,1136,1266,1396,1527,1657,1787,1918,2048,2178,2309,2439,2569,
                5500,655,742,872,1002,1133,1263,1393,1524,1654,1784,1915,2045,2175,2306,2436,2566,
                5600,652,739,869,999,1130,1260,1390,1521,1651,1781,1912,2042,2172,2303,2433,2563,
                5700,649,736,866,996,1127,1257,1387,1518,1648,1778,1909,2039,2169,2300,2430,2560,
                5800,646,733,863,993,1124,1254,1384,1515,1645,1775,1906,2036,2166,2297,2427,2557,
                5900,643,730,860,990,1121,1251,1381,1512,1642,1772,1903,2033,2163,2294,2424,2554,
                6000,640,727,857,987,1118,1248,1378,1509,1639,1769,1900,2030,2160,2291,2421,2551,
                6100,637,724,854,984,1115,1245,1375,1506,1636,1766,1897,2027,2157,2288,2418,2548,
                6200,634,721,851,981,1112,1242,1372,1503,1633,1763,1894,2024,2154,2285,2415,2545,
                6300,631,718,848,978,1109,1239,1369,1500,1630,1760,1891,2021,2151,2282,2412,2542,
                6400,628,715,845,975,1106,1236,1366,1497,1627,1757,1888,2018,2148,2279,2409,2539,
                6500,625,712,842,972,1103,1233,1363,1494,1624,1754,1885,2015,2145,2276,2406,2536,
                6600,622,709,839,969,1100,1230,1360,1491,1621,1751,1882,2012,2142,2273,2403,2533,
                6700,619,706,836,966,1097,1227,1357,1488,1618,1748,1879,2009,2139,2270,2400,2530,
                6800,616,703,833,963,1094,1224,1354,1485,1615,1745,1876,2006,2136,2267,2397,2527,
                6900,613,700,830,960,1091,1221,1351,1482,1612,1742,1873,2003,2133,2264,2394,2524,
                7000,610,697,827,957,1088,1218,1348,1479,1609,1739,1870,2000,2130,2261,2391,2521,
                7100,607,694,824,954,1085,1215,1345,1476,1606,1736,1867,1997,2127,2258,2388,2518,
                7200,604,691,821,951,1082,1212,1342,1473,1603,1733,1864,1994,2124,2255,2385,2515,
                7300,601,688,818,948,1079,1209,1339,1470,1600,1730,1861,1991,2121,2252,2382,2512,
                7400,598,685,815,945,1076,1206,1336,1467,1597,1727,1858,1988,2118,2249,2379,2509,
                7500,595,682,812,942,1073,1203,1333,1464,1594,1724,1855,1985,2115,2246,2376,2506,
                7600,592,679,809,939,1070,1200,1330,1461,1591,1721,1852,1982,2112,2243,2373,2503,
                7700,589,676,806,936,1067,1197,1327,1458,1588,1718,1849,1979,2109,2240,2370,2500,
                7800,586,673,803,933,1064,1194,1324,1455,1585,1715,1846,1976,2106,2237,2367,2497,
                7900,583,670,800,930,1061,1191,1321,1452,1582,1712,1843,1973,2103,2234,2364,2494,
                8000,512,667,797,927,1058,1188,1318,1449,1579,1709,1840,1970,2100,2231,2361,2491,
                8100,512,665,795,925,1056,1186,1316,1447,1577,1707,1838,1968,2098,2229,2359,2489,
                8200,512,663,793,923,1054,1184,1314,1445,1575,1705,1836,1966,2096,2227,2357,2487,
                8300,512,661,791,921,1052,1182,1312,1443,1573,1703,1834,1964,2094,2225,2355,2485,
                8400,512,659,789,919,1050,1180,1310,1441,1571,1701,1832,1962,2092,2223,2353,2483,
                8500,512,649,779,909,1040,1170,1300,1431,1561,1691,1822,1952,2082,2213,2343,2473,
                8600,512,639,769,899,1030,1160,1290,1421,1551,1681,1812,1942,2072,2203,2333,2463,
                8700,512,629,759,889,1020,1150,1280,1411,1541,1671,1802,1932,2062,2193,2323,2453,
                8800,512,619,749,879,1010,1140,1270,1401,1531,1661,1792,1922,2052,2183,2313,2443,
                8900,512,609,739,869,1000,1130,1260,1391,1521,1651,1782,1912,2042,2173,2303,2433,
                9000,512,599,729,859,990,1120,1250,1381,1511,1641,1772,1902,2032,2163,2293,2423,
                9100,512,589,719,849,980,1110,1240,1371,1501,1631,1762,1892,2022,2153,2283,2413,
                9200,512,579,709,839,970,1100,1230,1361,1491,1621,1752,1882,2012,2143,2273,2403,
                9300,512,569,699,829,960,1090,1220,1351,1481,1611,1742,1872,2002,2133,2263,2393,
                9400,512,512,689,819,950,1080,1210,1341,1471,1601,1732,1862,1992,2123,2253,2383,
                9500,512,512,679,809,940,1070,1200,1331,1461,1591,1722,1852,1982,2113,2243,2373,
                9600,512,512,669,799,930,1060,1190,1321,1451,1581,1712,1842,1972,2103,2233,2363,
                9700,512,512,659,789,920,1050,1180,1311,1441,1571,1702,1832,1962,2093,2223,2353,
                9800,512,512,649,779,910,1040,1170,1301,1431,1561,1692,1822,1952,2083,2213,2343,
                9900,512,512,639,769,900,1030,1160,1291,1421,1551,1682,1812,1942,2073,2203,2333,
                10000,512,512,629,759,890,1020,1150,1281,1411,1541,1672,1802,1932,2063,2193,2323};

        int[] R0 = {9,16,28,50,89,158,281,500,889,1581,2812,5000,8891,15811,28117,50000,
                500,6,21,65,207,655,2071,6548,20706,65479,207061,654786,2070615,6547859,20706147,65478586,207061469,
                550,7,22,69,218,689,2180,6892,21796,68925,217959,689248,2179594,6892483,21795944,68924827,217959441,
                600,7,23,73,229,726,2294,7255,22943,72552,229431,725524,2294310,7255245,22943099,72552450,229430990,
                650,8,24,76,242,764,2415,7637,24151,76371,241506,763710,2415063,7637100,24150631,76371000,241506306,
                700,8,25,80,254,804,2542,8039,25422,80391,254217,803905,2542172,8039053,25421716,80390526,254217164,
                750,8,27,85,268,846,2676,8462,26760,84622,267597,846216,2675970,8462161,26759701,84621606,267597015,
                800,9,28,89,282,891,2817,8908,28168,89075,281681,890754,2816811,8907537,28168107,89075375,281681068,
                850,9,30,94,297,938,2965,9376,29651,93764,296506,937636,2965064,9376355,29650639,93763552,296506387,
                900,10,31,99,312,987,3121,9870,31211,98698,312112,986985,3121120,9869848,31211199,98698476,312111987,
                1000,10,33,104,329,1039,3285,10389,32854,103893,328539,1038931,3285389,10389313,32853893,103893133,328538933,
                1100,11,35,109,346,1094,3458,10936,34583,109361,345830,1093612,3458305,10936119,34583046,109361193,345830456,
                1200,11,36,115,364,1151,3640,11512,36403,115117,364032,1151170,3640321,11511704,36403206,115117045,364032059,
                1300,12,38,121,383,1212,3832,12118,38319,121176,383192,1211758,3831916,12117584,38319164,121175837,383191641,
                1400,13,40,128,403,1276,4034,12755,40336,127554,403360,1275535,4033596,12755351,40335962,127553512,403359622,
                1500,13,42,134,425,1343,4246,13427,42459,134267,424589,1342669,4245891,13426685,42458908,134266855,424589076,
                1600,14,45,141,447,1413,4469,14133,44694,141334,446936,1413335,4469359,14133353,44693587,141333532,446935870,
                1700,15,47,149,470,1488,4705,14877,47046,148772,470459,1487721,4704588,14877214,47045881,148772138,470458810,
                1800,15,50,157,495,1566,4952,15660,49522,156602,495220,1566023,4952198,15660225,49521980,156602251,495219800,
                1900,16,52,165,521,1648,5213,16484,52128,164844,521284,1648445,5212840,16484447,52128400,164844475,521284000,
                2000,17,55,174,549,1735,5487,17352,54872,173520,548720,1735205,5487200,17352050,54872000,173520500,548720000,
                2100,18,58,183,578,1827,5776,18265,57760,182653,577600,1826532,5776000,18265316,57760000,182653158,577600000,
                2200,19,61,192,608,1923,6080,19227,60800,192266,608000,1922665,6080000,19226648,60800000,192266482,608000000,
                2300,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                2400,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                2500,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                2600,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                2700,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                2800,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                2900,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                3000,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                3100,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                3200,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                3300,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                3400,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                3500,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                3600,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                3700,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                3800,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                3900,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                4000,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                4100,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                4200,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                4300,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                4400,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                4500,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                4600,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                4700,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                4800,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                4900,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                5000,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                5100,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                5200,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                5300,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                5400,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                5500,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                5600,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                5700,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                5800,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                5900,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                6000,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                6100,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                6200,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                6300,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                6400,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                6500,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                6600,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                6700,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                6800,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                6900,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                7000,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                7100,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                7200,20,64,202,640,2024,6400,20239,64000,202386,640000,2023858,6400000,20238577,64000000,202385770,640000000,
                7300,19,62,196,621,1963,6208,19631,62080,196314,620800,1963142,6208000,19631420,62080000,196314197,620800000,
                7400,19,60,190,602,1904,6022,19042,60218,190425,602176,1904248,6021760,19042477,60217600,190424771,602176000,
                7500,18,58,185,584,1847,5841,18471,58411,184712,584111,1847120,5841107,18471203,58411072,184712028,584110720,
                7600,18,57,179,567,1792,5666,17917,56659,179171,566587,1791707,5665874,17917067,56658740,179170667,566587398,
                7700,17,55,174,550,1738,5496,17380,54959,173796,549590,1737955,5495898,17379555,54958978,173795547,549589776,
                7800,17,53,169,533,1686,5331,16858,53310,168582,533102,1685817,5331021,16858168,53310208,168581681,533102083,
                7900,16,52,164,517,1635,5171,16352,51711,163524,517109,1635242,5171090,16352423,51710902,163524230,517109021,
                8000,16,50,159,502,1586,5016,15862,50160,158619,501596,1586185,5015958,15861850,50159575,158618503,501595750,
                8100,15,49,154,487,1539,4865,15386,48655,153860,486548,1538599,4865479,15385995,48654788,153859948,486547878,
                8200,15,47,149,472,1492,4720,14924,47195,149244,471951,1492441,4719514,14924415,47195144,149244150,471951441,
                8300,14,46,145,458,1448,4578,14477,45779,144767,457793,1447668,4577929,14476683,45779290,144766825,457792898,
                8400,14,44,140,444,1404,4441,14042,44406,140424,444059,1404238,4440591,14042382,44405911,140423821,444059111,
                8500,13,43,136,431,1362,4307,13621,43074,136211,430737,1362111,4307373,13621111,43073734,136211106,430737338,
                8600,13,42,132,418,1321,4178,13212,41782,132125,417815,1321248,4178152,13212477,41781522,132124773,417815218,
                8700,13,41,128,405,1282,4053,12816,40528,128161,405281,1281610,4052808,12816103,40528076,128161030,405280761,
                8800,12,39,124,393,1243,3931,12432,39312,124316,393122,1243162,3931223,12431620,39312234,124316199,393122338,
                8900,12,38,121,381,1206,3813,12059,38133,120587,381329,1205867,3813287,12058671,38132867,120586713,381328668,
                9000,12,37,117,370,1170,3699,11697,36989,116969,369889,1169691,3698888,11696911,36988881,116969111,369888808,
                9100,11,36,113,359,1135,3588,11346,35879,113460,358792,1134600,3587921,11346004,35879214,113460038,358792144,
                9200,11,35,110,348,1101,3480,11006,34803,110056,348028,1100562,3480284,11005624,34802838,110056237,348028379,
                9300,11,34,107,338,1068,3376,10675,33759,106755,337588,1067545,3375875,10675455,33758753,106754550,337587528,
                9400,10,33,104,327,1036,3275,10355,32746,103552,327460,1035519,3274599,10355191,32745990,103551913,327459902,
                9500,10,32,100,318,1004,3176,10045,31764,100445,317636,1004454,3176361,10044536,31763611,100445356,317636105,
                9600,10,31,97,308,974,3081,9743,30811,97432,308107,974320,3081070,9743200,30810702,97431995,308107022,
                9700,9,30,95,299,945,2989,9451,29886,94509,298864,945090,2988638,9450904,29886381,94509035,298863811,
                9800,9,29,92,290,917,2899,9167,28990,91674,289898,916738,2898979,9167376,28989790,91673764,289897897,
                9900,9,28,89,281,889,2812,8892,28120,88924,281201,889236,2812010,8892355,28120096,88923551,281200960,
                10000,9,27,86,273,863,2728,8626,27276,86256,272765,862558,2727649,8625584,27276493,86255845,272764931};

        int[] R21 = {89,158,281,500,889,1581,2812,5000,8891,15811,28117,50000,88914,158114,281171,500000,
                500,5,16,52,164,520,1645,5201,16447,52011,164475,520115,1644748,5201149,16447477,52011489,164474771,
                550,5,17,55,173,547,1731,5475,17313,54749,173131,547489,1731313,5474894,17313134,54748936,173131338,
                600,6,18,58,182,576,1822,5763,18224,57630,182244,576305,1822435,5763046,18224351,57630459,182243514,
                650,6,19,61,192,607,1918,6066,19184,60664,191835,606636,1918353,6066364,19183528,60663641,191835277,
                700,6,20,64,202,639,2019,6386,20193,63856,201932,638565,2019319,6385646,20193187,63856464,201931871,
                750,7,21,67,213,672,2126,6722,21256,67217,212560,672173,2125599,6721733,21255986,67217331,212559864,
                800,7,22,71,224,708,2237,7076,22375,70755,223747,707551,2237472,7075509,22374723,70755085,223747225,
                850,7,24,74,236,745,2355,7448,23552,74479,235523,744790,2355234,7447904,23552340,74479037,235523395,
                900,8,25,78,248,784,2479,7840,24792,78399,247919,783990,2479194,7839899,24791936,78398986,247919363,
                1000,8,26,83,261,825,2610,8253,26097,82525,260968,825252,2609678,8252525,26096775,82525249,260967751,
                1100,9,27,87,275,869,2747,8687,27470,86869,274703,868687,2747029,8686868,27470290,86868683,274702896,
                1200,9,29,91,289,914,2892,9144,28916,91441,289161,914407,2891609,9144072,28916094,91440719,289160943,
                1300,10,30,96,304,963,3044,9625,30438,96253,304380,962534,3043799,9625339,30437994,96253388,304379940,
                1400,10,32,101,320,1013,3204,10132,32040,101319,320400,1013194,3203999,10131936,32039994,101319356,320399937,
                1500,11,34,107,337,1067,3373,10665,33726,106652,337263,1066520,3372631,10665195,33726309,106651954,337263091,
                1600,11,36,112,355,1123,3550,11227,35501,112265,355014,1122652,3550138,11226521,35501378,112265215,355013780,
                1700,12,37,118,374,1182,3737,11817,37370,118174,373699,1181739,3736987,11817391,37369872,118173910,373698716,
                1800,12,39,124,393,1244,3934,12439,39337,124394,393367,1243936,3933671,12439359,39336707,124393590,393367070,
                1900,13,41,131,414,1309,4141,13094,41407,130941,414071,1309406,4140706,13094062,41407060,130940621,414070600,
                2000,14,44,138,436,1378,4359,13783,43586,137832,435864,1378322,4358638,13783223,43586379,137832232,435863789,
                2100,14,46,145,459,1451,4588,14509,45880,145087,458804,1450866,4588040,14508656,45880399,145086560,458803988,
                2200,15,48,153,483,1527,4830,15272,48295,152723,482952,1527227,4829516,15272270,48295157,152722695,482951567,
                2300,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                2400,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                2500,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                2600,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                2700,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                2800,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                2900,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                3000,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                3100,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                3200,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                3300,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                3400,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                3500,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                3600,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                3700,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                3800,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                3900,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                4000,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                4100,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                4200,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                4300,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                4400,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                4500,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                4600,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                4700,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                4800,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                4900,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                5000,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                5100,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                5200,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                5300,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                5400,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                5500,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                5600,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                5700,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                5800,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                5900,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                6000,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                6100,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                6200,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                6300,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                6400,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                6500,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                6600,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                6700,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                6800,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                6900,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                7000,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                7100,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                7200,16,51,161,508,1608,5084,16076,50837,160761,508370,1607607,5083701,16076073,50837007,160760732,508370070,
                7300,15,49,156,493,1559,4931,15594,49312,155938,493119,1559379,4931190,15593791,49311897,155937910,493118968,
                7400,15,48,151,478,1513,4783,15126,47833,151260,478325,1512598,4783254,15125977,47832540,151259772,478325399,
                7500,14,46,147,464,1467,4640,14672,46398,146722,463976,1467220,4639756,14672198,46397564,146721979,463975637,
                7600,14,45,142,450,1423,4501,14232,45006,142320,450056,1423203,4500564,14232032,45005637,142320320,450056368,
                7700,14,44,138,437,1381,4366,13805,43655,138051,436555,1380507,4365547,13805071,43655468,138050710,436554677,
                7800,13,42,134,423,1339,4235,13391,42346,133909,423458,1339092,4234580,13390919,42345804,133909189,423458037,
                7900,13,41,130,411,1299,4108,12989,41075,129892,410754,1298919,4107543,12989191,41075430,129891913,410754296,
                8000,12,40,126,398,1260,3984,12600,39843,125995,398432,1259952,3984317,12599516,39843167,125995156,398431667,
                8100,12,39,122,386,1222,3865,12222,38648,122215,386479,1222153,3864787,12221530,38647872,122215301,386478717,
                8200,12,37,119,375,1185,3749,11855,37488,118549,374884,1185488,3748844,11854884,37488436,118548842,374884355,
                8300,11,36,115,364,1150,3636,11499,36364,114992,363638,1149924,3636378,11499238,36363782,114992377,363637825,
                8400,11,35,112,353,1115,3527,11154,35273,111543,352729,1115426,3527287,11154261,35272869,111542606,352728690,
                8500,11,34,108,342,1082,3421,10820,34215,108196,342147,1081963,3421468,10819633,34214683,108196327,342146829,
                8600,10,33,105,332,1050,3319,10495,33188,104950,331882,1049504,3318824,10495044,33188242,104950438,331882424,
                8700,10,32,102,322,1018,3219,10180,32193,101802,321926,1018019,3219260,10180192,32192595,101801924,321925951,
                8800,10,31,99,312,987,3123,9875,31227,98748,312268,987479,3122682,9874787,31226817,98747867,312268173,
                8900,9,30,96,303,958,3029,9579,30290,95785,302900,957854,3029001,9578543,30290013,95785431,302900128,
                9000,9,29,93,294,929,2938,9291,29381,92912,293813,929119,2938131,9291187,29381312,92911868,293813124,
                9100,9,28,90,285,901,2850,9012,28500,90125,284999,901245,2849987,9012451,28499873,90124512,284998730,
                9200,9,28,87,276,874,2764,8742,27645,87421,276449,874208,2764488,8742078,27644877,87420776,276448768,
                9300,8,27,85,268,848,2682,8480,26816,84798,268155,847982,2681553,8479815,26815531,84798153,268155305,
                9400,8,26,82,260,823,2601,8225,26011,82254,260111,822542,2601106,8225421,26011065,82254209,260110646,
                9500,8,25,80,252,798,2523,7979,25231,79787,252307,797866,2523073,7978658,25230733,79786582,252307327,
                9600,8,24,77,245,774,2447,7739,24474,77393,244738,773930,2447381,7739298,24473811,77392985,244738107,
                9700,7,24,75,237,751,2374,7507,23740,75071,237396,750712,2373960,7507120,23739596,75071195,237395964,
                9800,7,23,73,230,728,2303,7282,23027,72819,230274,728191,2302741,7281906,23027408,72819059,230274085,
                9900,7,22,71,223,706,2234,7063,22337,70634,223366,706345,2233659,7063449,22336586,70634488,223365862,
                10000,7,22,69,217,685,2167,6852,21666,68515,216665,685155,2166649,6851545,21666489,68515453,216664886};
        int[] R42 = {889,1581,2812,5000,8891,15811,28117,50000,88914,158114,281171,500000,889140,1581139,2811707,5000000,
                500,4,13,41,131,413,1306,4131,13065,41314,130647,413142,1306470,4131419,13064695,41314195,130646954,
                550,4,14,43,138,435,1375,4349,13752,43489,137523,434886,1375231,4348863,13752311,43488626,137523110,
                600,5,14,46,145,458,1448,4578,14476,45778,144761,457775,1447612,4577750,14476117,45777501,144761168,
                650,5,15,48,152,482,1524,4819,15238,48187,152380,481868,1523802,4818684,15238018,48186843,152380177,
                700,5,16,51,160,507,1604,5072,16040,50723,160400,507230,1604002,5072299,16040019,50722993,160400187,
                750,5,17,53,169,534,1688,5339,16884,53393,168842,533926,1688423,5339262,16884230,53392624,168842302,
                800,6,18,56,178,562,1777,5620,17773,56203,177729,562028,1777287,5620276,17772874,56202762,177728739,
                850,6,19,59,187,592,1871,5916,18708,59161,187083,591608,1870829,5916080,18708288,59160802,187082883,
                900,6,20,62,197,623,1969,6227,19693,62275,196929,622745,1969294,6227453,19692935,62274528,196929350,
                1000,6,21,66,207,656,2073,6555,20729,65552,207294,655521,2072941,6555214,20729405,65552135,207294053,
                1100,7,22,69,218,690,2182,6900,21820,69002,218204,690022,2182043,6900225,21820427,69002248,218204266,
                1200,7,23,73,230,726,2297,7263,22969,72634,229689,726339,2296887,7263394,22968870,72633945,229688701,
                1300,8,24,76,242,765,2418,7646,24178,76457,241778,764568,2417776,7645678,24177758,76456784,241777580,
                1400,8,25,80,255,805,2545,8048,25450,80481,254503,804808,2545027,8048083,25450272,80480825,254502716,
                1500,8,27,85,268,847,2679,8472,26790,84717,267898,847167,2678976,8471666,26789760,84716658,267897596,
                1600,9,28,89,282,892,2820,8918,28200,89175,281997,891754,2819975,8917543,28199747,89175430,281997469,
                1700,9,30,94,297,939,2968,9387,29684,93869,296839,938689,2968394,9386887,29683944,93868873,296839441,
                1800,10,31,99,312,988,3125,9881,31246,98809,312463,988093,3124626,9880934,31246257,98809340,312462570,
                1900,10,33,104,329,1040,3289,10401,32891,104010,328908,1040098,3289080,10400983,32890797,104009832,328907968,
                2000,11,35,109,346,1095,3462,10948,34622,109484,346219,1094840,3462189,10948403,34621891,109484034,346218914,
                2100,11,36,115,364,1152,3644,11525,36444,115246,364441,1152464,3644410,11524635,36444096,115246351,364440962,
                2200,12,38,121,384,1213,3836,12131,38362,121312,383622,1213119,3836221,12131195,38362207,121311949,383622065,
                2300,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                2400,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                2500,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                2600,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                2700,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                2800,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                2900,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                3000,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                3100,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                3200,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                3300,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                3400,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                3500,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                3600,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                3700,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                3800,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                3900,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                4000,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                4100,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                4200,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                4300,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                4400,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                4500,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                4600,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                4700,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                4800,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                4900,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                5000,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                5100,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                5200,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                5300,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                5400,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                5500,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                5600,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                5700,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                5800,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                5900,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                6000,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                6100,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                6200,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                6300,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                6400,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                6500,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                6600,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                6700,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                6800,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                6900,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                7000,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                7100,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                7200,13,40,128,404,1277,4038,12770,40381,127697,403813,1276968,4038127,12769679,40381270,127696788,403812700,
                7300,12,39,124,392,1239,3917,12387,39170,123866,391698,1238659,3916983,12386588,39169832,123865885,391698319,
                7400,12,38,120,380,1201,3799,12015,37995,120150,379947,1201499,3799474,12014991,37994737,120149908,379947370,
                7500,12,37,117,369,1165,3685,11655,36855,116545,368549,1165454,3685489,11654541,36854895,116545411,368548949,
                7600,11,36,113,357,1130,3575,11305,35749,113049,357492,1130490,3574925,11304905,35749248,113049048,357492480,
                7700,11,35,110,347,1097,3468,10966,34677,109658,346768,1096576,3467677,10965758,34676771,109657577,346767706,
                7800,11,34,106,336,1064,3364,10637,33636,106368,336365,1063678,3363647,10636785,33636467,106367850,336364675,
                7900,10,33,103,326,1032,3263,10318,32627,103177,326274,1031768,3262737,10317681,32627373,103176814,326273734,
                8000,10,32,100,316,1001,3165,10008,31649,100082,316486,1000815,3164855,10008151,31648552,100081510,316485522,
                8100,10,31,97,307,971,3070,9708,30699,97079,306991,970791,3069910,9707906,30699096,97079064,306990957,
                8200,9,30,94,298,942,2978,9417,29778,94167,297781,941667,2977812,9416669,29778123,94166693,297781228,
                8300,9,29,91,289,913,2888,9134,28885,91342,288848,913417,2888478,9134169,28884779,91341692,288847791,
                8400,9,28,89,280,886,2802,8860,28018,88601,280182,886014,2801824,8860144,28018236,88601441,280182357,
                8500,8,27,86,272,859,2718,8594,27178,85943,271777,859434,2717769,8594340,27177689,85943398,271776887,
                8600,8,26,83,264,834,2636,8337,26362,83365,263624,833651,2636236,8336510,26362358,83365096,263623580,
                8700,8,26,81,256,809,2557,8086,25571,80864,255715,808641,2557149,8086414,25571487,80864143,255714873,
                8800,8,25,78,248,784,2480,7844,24804,78438,248043,784382,2480434,7843822,24804343,78438219,248043427,
                8900,8,24,76,241,761,2406,7609,24060,76085,240602,760851,2406021,7608507,24060212,76085072,240602124,
                9000,7,23,74,233,738,2334,7380,23338,73803,233384,738025,2333841,7380252,23338406,73802520,233384060,
                9100,7,23,72,226,716,2264,7159,22638,71588,226383,715884,2263825,7158844,22638254,71588444,226382538,
                9200,7,22,69,220,694,2196,6944,21959,69441,219591,694408,2195911,6944079,21959106,69440791,219591062,
                9300,7,21,67,213,674,2130,6736,21300,67358,213003,673576,2130033,6735757,21300333,67357567,213003330,
                9400,6,21,65,207,653,2066,6534,20661,65337,206613,653368,2066132,6533684,20661323,65336840,206613230,
                9500,6,20,63,200,634,2004,6338,20041,63377,200415,633767,2004148,6337674,20041483,63376735,200414833,
                9600,6,19,61,194,615,1944,6148,19440,61475,194402,614754,1944024,6147543,19440239,61475433,194402388,
                9700,6,19,60,189,596,1886,5963,18857,59631,188570,596312,1885703,5963117,18857032,59631170,188570317,
                9800,6,18,58,183,578,1829,5784,18291,57842,182913,578422,1829132,5784223,18291321,57842235,182913207,
                9900,6,18,56,177,561,1774,5611,17743,56107,177426,561070,1774258,5610697,17742581,56106968,177425811,
                10000,5,17,54,172,544,1721,5442,17210,54424,172103,544238,1721030,5442376,17210304,54423759,172103037};
        int[] RLNA = {1,2,3,5,9,16,28,50,89,158,281,500,889,1581,2812,5000,
                500,7,23,73,232,735,2323,7347,23233,73468,232327,734682,2323268,7346818,23232679,73468182,232326789,
                550,8,24,77,245,773,2446,7733,24455,77335,244555,773349,2445545,7733493,24455451,77334928,244554515,
                600,8,26,81,257,814,2574,8141,25743,81405,257426,814052,2574258,8140519,25742581,81405187,257425805,
                650,8,27,86,271,857,2710,8569,27097,85690,270975,856897,2709745,8568967,27097453,85689671,270974532,
                700,9,29,90,285,902,2852,9020,28524,90200,285236,901997,2852363,9019965,28523635,90199653,285236349,
                750,9,30,95,300,949,3002,9495,30025,94947,300249,949470,3002488,9494700,30024879,94947004,300248789,
                800,10,32,100,316,999,3161,9994,31605,99944,316051,999442,3160514,9994421,31605136,99944214,316051356,
                850,10,33,105,333,1052,3327,10520,33269,105204,332686,1052044,3326856,10520444,33268564,105204436,332685638,
                900,11,35,111,350,1107,3502,11074,35020,110742,350195,1107415,3501954,11074151,35019541,110741512,350195409,
                1000,12,37,117,369,1166,3686,11657,36863,116570,368627,1165700,3686267,11657001,36862675,116570012,368626746,
                1100,12,39,123,388,1227,3880,12271,38803,122705,388028,1227053,3880282,12270528,38802815,122705276,388028154,
                1200,13,41,129,408,1292,4085,12916,40845,129163,408451,1291634,4084507,12916345,40845069,129163449,408450688,
                1300,13,43,136,430,1360,4299,13596,42995,135962,429948,1359615,4299481,13596152,42994809,135961525,429948093,
                1400,14,45,143,453,1431,4526,14312,45258,143117,452577,1431174,4525769,14311739,45257694,143117395,452576940,
                1500,15,48,151,476,1506,4764,15065,47640,150650,476397,1506499,4763968,15064989,47639678,150649889,476396779,
                1600,16,50,159,501,1586,5015,15858,50147,158579,501470,1585788,5014703,15857883,50147029,158578831,501470293,
                1700,16,53,167,528,1669,5279,16693,52786,166925,527863,1669251,5278635,16692508,52786347,166925085,527863467,
                1800,17,56,176,556,1757,5556,17571,55565,175711,555646,1757106,5556458,17571062,55564575,175710616,555645755,
                1900,18,58,185,585,1850,5849,18496,58489,184959,584890,1849585,5848903,18495854,58489027,184958543,584890268,
                2000,19,62,195,616,1947,6157,19469,61567,194693,615674,1946932,6156740,19469320,61567397,194693203,615673966,
                2100,20,65,205,648,2049,6481,20494,64808,204940,648078,2049402,6480779,20494021,64807786,204940214,648077859,
                2200,21,68,216,682,2157,6822,21573,68219,215727,682187,2157265,6821872,21572654,68218722,215726541,682187220,
                2300,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                2400,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                2500,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                2600,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                2700,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                2800,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                2900,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                3000,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                3100,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                3200,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                3300,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                3400,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                3500,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                3600,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                3700,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                3800,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                3900,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                4000,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                4100,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                4200,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                4300,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                4400,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                4500,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                4600,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                4700,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                4800,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                4900,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                5000,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                5100,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                5200,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                5300,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                5400,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                5500,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                5600,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                5700,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                5800,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                5900,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                6000,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                6100,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                6200,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                6300,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                6400,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                6500,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                6600,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                6700,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                6800,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                6900,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                7000,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                7100,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                7200,22,72,227,718,2271,7181,22708,71809,227081,718092,2270806,7180918,22708057,71809181,227080569,718091811,
                7300,22,70,220,697,2203,6965,22027,69655,220268,696549,2202682,6965491,22026815,69654906,220268152,696549056,
                7400,21,68,214,676,2137,6757,21366,67565,213660,675653,2136601,6756526,21366011,67565258,213660107,675652585,
                7500,20,66,207,655,2073,6554,20725,65538,207250,655383,2072503,6553830,20725030,65538301,207250304,655383007,
                7600,20,64,201,636,2010,6357,20103,63572,201033,635722,2010328,6357215,20103280,63572152,201032795,635721517,
                7700,19,62,195,617,1950,6166,19500,61665,195002,616650,1950018,6166499,19500181,61664987,195001811,616649871,
                7800,19,60,189,598,1892,5982,18915,59815,189152,598150,1891518,5981504,18915176,59815038,189151757,598150375,
                7900,18,58,183,580,1835,5802,18348,58021,183477,580206,1834772,5802059,18347720,58020586,183477204,580205864,
                8000,18,56,178,563,1780,5628,17797,56280,177973,562800,1779729,5627997,17797289,56279969,177972888,562799688,
                8100,17,55,173,546,1726,5459,17263,54592,172634,545916,1726337,5459157,17263370,54591570,172633701,545915698,
                8200,17,53,167,530,1675,5295,16745,52954,167455,529538,1674547,5295382,16745469,52953823,167454690,529538227,
                8300,16,51,162,514,1624,5137,16243,51365,162431,513652,1624310,5136521,16243105,51365208,162431050,513652080,
                8400,16,50,158,498,1576,4982,15756,49824,157558,498243,1575581,4982425,15755812,49824252,157558118,498242517,
                8500,15,48,153,483,1528,4833,15283,48330,152831,483295,1528314,4832952,15283137,48329524,152831375,483295242,
                8600,15,47,148,469,1482,4688,14825,46880,148246,468796,1482464,4687964,14824643,46879638,148246433,468796385,
                8700,14,45,144,455,1438,4547,14380,45473,143799,454732,1437990,4547325,14379904,45473249,143799040,454732493,
                8800,14,44,139,441,1395,4411,13949,44109,139485,441091,1394851,4410905,13948507,44109052,139485069,441090518,
                8900,13,43,135,428,1353,4279,13530,42786,135301,427858,1353005,4278578,13530052,42785780,135300517,427857803,
                9000,13,42,131,415,1312,4150,13124,41502,131242,415022,1312415,4150221,13124150,41502207,131241502,415022069,
                9100,13,40,127,403,1273,4026,12730,40257,127304,402571,1273043,4025714,12730426,40257141,127304257,402571407,
                9200,12,39,123,390,1235,3905,12349,39049,123485,390494,1234851,3904943,12348513,39049426,123485129,390494264,
                9300,12,38,120,379,1198,3788,11978,37878,119781,378779,1197806,3787794,11978058,37877944,119780575,378779436,
                9400,11,37,116,367,1162,3674,11619,36742,116187,367416,1161872,3674161,11618716,36741605,116187158,367416053,
                9500,11,36,113,356,1127,3564,11270,35639,112702,356394,1127015,3563936,11270154,35639357,112701543,356393572,
                9600,11,35,109,346,1093,3457,10932,34570,109320,345702,1093205,3457018,10932050,34570176,109320497,345701765,
                9700,10,34,106,335,1060,3353,10604,33533,106041,335331,1060409,3353307,10604088,33533071,106040882,335330712,
                9800,10,33,103,325,1029,3253,10286,32527,102860,325271,1028597,3252708,10285966,32527079,102859655,325270790,
                9900,10,32,100,316,998,3155,9977,31551,99774,315513,997739,3155127,9977387,31551267,99773866,315512667,
                10000,10,31,97,306,968,3060,9678,30605,96781,306047,967806,3060473,9678065,30604729,96780650,306047287};




        if(Peak_RMS.equals("P") && LNA == 0)
        {
            return P0;
        }
        else if (Peak_RMS.equals("P") && LNA == 1)
        {
            return P21;
        }
        else if (Peak_RMS.equals("P") && LNA == 2)
        {
            return P42;
        }
        else if (Peak_RMS.equals("P") && LNA == 3)
        {
            return PLNA;
        }
        else if (Peak_RMS.equals("R") && LNA == 0)
        {
            return R0;
        }
        else if (Peak_RMS.equals("R") && LNA == 1)
        {
            return R21;
        }
        else if (Peak_RMS.equals("R") && LNA == 2)
        {
            return R42;
        }
        else if (Peak_RMS.equals("R") && LNA == 3)
        {
            return RLNA;
        }
        else{
            throw  new IllegalStateException("Peak-RMS is: " + Peak_RMS +"and LNA is" + LNA);
        }

    }
}
