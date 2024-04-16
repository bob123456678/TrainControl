package util;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Various helpful conversion functions
 * @author Adam
 */
public class Conversion
{
    public static String convertSecondsToHMmSs(long ms)
    {
        long seconds = ms / 1000;
        
        long s = seconds % 60;
        long m = (seconds / 60) % 60;
        long h = (seconds / (60 * 60));
        
        return String.format("%d:%02d:%02d", h,m,s);
    }
    
    public static String convertSecondsToDate(long timestamp)
    {
        if (timestamp == 0) return "Never";
        
        LocalDateTime i = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime();
         
        return i.toString().split("T")[0];
    }
    
    public static String convertSecondsToDatetime(long timestamp)
    {
        if (timestamp == 0) return "Never";
        
        LocalDateTime i = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime();
         
        return i.toString().split("\\.")[0].replace("T", " ");
    }
    
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
