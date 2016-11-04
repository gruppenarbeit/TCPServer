package group_project.test001;

/**
 * Created by andre_eggli on 7/3/16.
 */

public class WifiPackage {
    byte[] rowData;

    public WifiPackage(byte[] rowData) {
        this.rowData = rowData;
    }


    public byte[] getRowData() {
        return rowData;
    }

    public String toString () { // Debug only
        int maxleng = rowData.length;
        if(maxleng > 200) {
            maxleng = 200;
        }

        String messageType = "\nWifiPackage.toString: ";
        for (int i = 0; i < maxleng; i++) {
            messageType += ( rowData[i] & 0xFF)  + "#";
        }
        messageType += ",\n";
        return messageType;
    }
}
