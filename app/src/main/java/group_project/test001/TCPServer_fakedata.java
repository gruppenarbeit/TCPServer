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
    final byte[] RD16 = "RD16".getBytes();
    final byte[] PEND = "PEND".getBytes();
    final byte[] TIME = "TIME".getBytes();
    final byte[] DRDY = "DRDY".getBytes();
    final byte[] DETV = "DETV".getBytes();
    final byte[] CALD = "CALD".getBytes();

    final byte[] device_id = {0, 0, 0, 125};
    byte[] battery_charge = {90}; // Prozent
    byte[] battery_voltage = {0, 10}; // = 10mV
    byte[] sequence_number = {77, 77, 77, 77}; // TODO: Make dynamic
    byte[] RTC = {0, 0, 0, 0, 0, 0};
    final byte[] anzahl_tabellen= {8};
    final byte[] freqs_N = {0, 100};
    final byte[] levels_M = {0 ,16};
    byte[] current_Pack = null;
    byte[] frequencies = reserviert(2, 0);
    byte[] LNA = {0};

    public enum STATE {Start, Waiting, Time, Scan, Detv, Callibrate, Stop}

    public TCPServer_fakedata(final WifiDataBuffer wifiDataBuffer) throws IllegalStateException {
        Log.d("TCPServer","Constructor of TCPServer called");
        this.wifiDataBuffer = wifiDataBuffer;


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
                                        Log.d("TCPServer", "State Scan not yet implemented :(");
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
                                        Log.d("TCPServer", "State Callibrate not yet implemented :(");
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
        checkForCorrectness(DataPack); // RD16, length, PEND
        byte[] HeaderofData = {DataPack[4], DataPack[5], DataPack[6], DataPack[7]};
        Log.d("TCPServer","TCPServer_fake did send a DataPackage of Type '"+ new String(HeaderofData) +"' to Android");
        wifiDataBuffer.enque_FromESP(DataPack);
    }

    private STATE getNextState(STATE old_state) {
        STATE new_state;
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
                            break;
                        case "DETV":
                            new_state = STATE.Detv;
                            frequencies = split_packet(14, 13+2*current_Pack[13], current_Pack);
                            break;
                        case "SCAN":
                            if(old_state == STATE.Scan) {
                                int currentscanfreq = byteArray2int(frequencies);
                                if (currentscanfreq >= 10000) {
                                    new_state =  STATE.Waiting;
                                } else {
                                    frequencies = int2byteArray(currentscanfreq + 100, 2);
                                    new_state = STATE.Scan;
                                }
                            } else {
                                frequencies = int2byteArray(500, 2);
                                new_state = STATE.Scan;
                            }
                            break;
                        default:
                            throw new IllegalStateException("Heder " + new String(header) + " not valid in getNextState");
                    }
                }
                else {
                    new_state = STATE.Waiting;
                }
                break;
        }
        return new_state;
    }

//    private byte[] int2byteArray(int number, int digits) {
//        for(int loopcnt = 0; loopcnt < digits; ++loopcnt) {
//            byte b = (byte) (number & 0xFF);
//        }
//
//    }
//
//    private int byteArray2int(byte[] frequencies) {
//        int digit = 0;
//        int result = 0;
//        for(byte b : frequencies) {
//            int I = (int) b;
//            if (I < 0) {
//                I += 256;
//            }
//            result += I * 10^digit;
//            digit ++;
//        }
//        return result;
//    }

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

    private byte[] Kalibrationstabelle()
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
        byte[] header = split_packet(3, 7, received_from_ESP);
        if ((new String(header).equals("DRDY")) && !(received_from_ESP.length == 32)) {
            throw new IllegalArgumentException("Header is not coherent with length");
        } else if ((new String(header).equals("CALD")) && !(received_from_ESP.length == 53423)) {
            throw new IllegalArgumentException("Header is not coherent with length");
        } else if ((new String(header).equals("SCAN")) && !(received_from_ESP.length == 56)) {
            throw new IllegalArgumentException("Header is not coherent with length");
        } else if ((new String(header).equals("DETV")) && !(received_from_ESP.length == 56)) {
            throw new IllegalArgumentException("Header is not coherent with length");
        } else if ((new String(header).equals("TIME")) && !(received_from_ESP.length == 95)) {
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

            //Todo
            if (headerString.endsWith("CALD")) {
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
                inStreamBuffer.write(Kalibrationstabelle());
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
