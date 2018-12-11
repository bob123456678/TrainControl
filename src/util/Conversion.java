package util;

/**
 * Various helpful conversion functions
 * @author Adam
 */
public class Conversion
{
    public static String bytesToBin(byte[] bytes)
    {
        String s = "";
        for ( int j = 0; j < bytes.length; j++ ) {
            s += String.format("%8s", Integer.toBinaryString((int) bytes[j] & 0xFF)).replace(' ', '0') + ((j < bytes.length - 1) ? "|" : "");
        }

        return s;
    }

    public static String bytesToHex(byte[] bytes) {
        String s = "";
        for ( int j = 0; j < bytes.length; j++ ) {
            s += String.format("0x%2s", Integer.toHexString((int) bytes[j] & 0xFF)).replace(' ', '0') + ((j < bytes.length - 1) ? "|" : "");
        }

        return s;
    }

    public static String intToHex(Integer b)
    {    
        return "0x" + Integer.toHexString(b);
    }
}
