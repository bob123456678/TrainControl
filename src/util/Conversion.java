package util;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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

    public static String bytesToHex(byte[] bytes)
    {
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
    
    /**
     * Generates a hash of the current working directory
     * @param lastN
     * @return 
     */
    public static String getFolderHash(int lastN)
    {
        String out = "";
        try
        {
            out = Conversion.bytesToHex(MessageDigest.getInstance("SHA1").digest(new File("").getAbsolutePath().getBytes()));
            out = out.replaceAll("\\|", "").replaceAll("0x", "");
        }
        catch (NoSuchAlgorithmException e)
        {
            System.out.println(e.getMessage());
        }
        
        if (lastN > 0 && lastN < out.length())
        {
            out = out.substring(out.length() - lastN);
        }
                
        return out;
    }
}
