package org.traincontrol.util;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
    /**
     * Formats a duration given in MILLISECONDS as h:mm:ss.  The name says seconds but every caller passes milliseconds;
     * documented rather than renamed, because the method is public and a silent rename is a worse trap.
     *
     * @param ms the duration in milliseconds
     * @return h:mm:ss
     */
    public static String convertSecondsToHMmSs(long ms)
    {
        long seconds = ms / 1000;
        
        long s = seconds % 60;
        long m = (seconds / 60) % 60;
        long h = (seconds / (60 * 60));
        
        return String.format("%d:%02d:%02d", h,m,s);
    }
    
    public static String convertSecondsToHMm(long ms)
    {
        long seconds = ms / 1000;
        
        long m = (seconds / 60) % 60;
        long h = (seconds / (60 * 60));
        
        return String.format("%d:%02d", h,m);
    }
    
    public static String convertSecondsToDate(long timestamp)
    {
        if (timestamp == 0) return I18n.f("ui.never");
        
        LocalDateTime i = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime();
         
        return i.toString().split("T")[0];
    }
    
    public static String convertSecondsToDatetime(long timestamp)
    {
        if (timestamp == 0) return I18n.f("ui.never");
        
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
     * Reads a locomotive address as typed: "0x" means hex for an MFX decoder and decimal, so not a number, for any other.
     * @param text what was typed
     * @param mfx whether the decoder is MFX
     * @return the address as typed, sign included
     * @throws NumberFormatException when it is not a number
     */
    public static int parseTypedAddress(String text, boolean mfx)
    {
        return mfx && text.contains("0x") ? Integer.parseInt(text.replace("0x", ""), 16) : Integer.parseInt(text);
    }
    
    /**
     * The charset Java 8 read and wrote text in by default on this machine: Java 18 and later default to UTF-8 and
     * name the machine's own as native.encoding (FR-236).
     */
    public static Charset nativeCharset()
    {
        try
        {
            return Charset.forName(System.getProperty("native.encoding"));
        }
        catch (RuntimeException none)
        {
            return Charset.defaultCharset();
        }
    }

    /**
     * The hash of the current working directory that its settings are kept under: the path in the native charset, as
     * Java 8 hashed it, unless only the UTF-8 hash Java 18 and later gave a non-English path has settings (FR-236).
     */
    public static String getFolderHash(int lastN)
    {
        String path = new File("").getAbsolutePath();
        String hash = folderHash(path, nativeCharset(), lastN), java18 = folderHash(path, StandardCharsets.UTF_8, lastN);

        return hash.equals(java18) || hasSettings(hash) || !hasSettings(java18) ? hash : java18;
    }

    /** Whether any setting of the windows' node has this hash: the package by name, so util compiles without gui. */
    private static boolean hasSettings(String hash)
    {
        String run = System.getProperty(Util.PREFERENCES_PROPERTY);

        try
        {
            for (String key : Util.preferencesFor("org.traincontrol.gui", run).keys())
            {
                if (key.endsWith(hash)) return true;
            }
        }
        catch (java.util.prefs.BackingStoreException unreadable)
        {
        }

        return false;
    }

    /** The last lastN hex digits of the SHA-1 of a path in this charset. */
    private static String folderHash(String path, Charset charset, int lastN)
    {
        String out = "";
        try
        {
            out = Conversion.bytesToHex(MessageDigest.getInstance("SHA1").digest(path.getBytes(charset)));
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
    
    /**
     * The numeric prefix of one dotted version component, so a suffixed release name ("0 (Beta)" from "v2.8.0 (Beta)")
     * cannot make Integer.parseInt throw and silence update announcements.  No leading digits counts as 0.
     */
    private static int parseVersionComponent(String component)
    {
        int end = 0;

        while (end < component.length() && Character.isDigit(component.charAt(end))) end++;

        return end == 0 ? 0 : Integer.parseInt(component.substring(0, end));
    }

    /**
     * Compared version numbers, i.e. 1.2.3
     * Returns 1 if version1 is higher than version2
     */
    public static int compareVersions(String version1, String version2)
    {
        String[] v1 = version1.split("\\.");
        String[] v2 = version2.split("\\.");

        int length = Math.max(v1.length, v2.length);
        
        for (int i = 0; i < length; i++)
        {
            int v1Component = i < v1.length ? parseVersionComponent(v1[i]) : 0;
            int v2Component = i < v2.length ? parseVersionComponent(v2[i]) : 0;

            if (v1Component < v2Component)
            {
                return -1;
            }
            
            if (v1Component > v2Component)
            {
                return 1;
            }
        }
        
        return 0;
    }
}
