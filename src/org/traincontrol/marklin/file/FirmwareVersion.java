package org.traincontrol.marklin.file;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Central Station's software version, as /app/api/info reports it ("2.6.1 (Build 3)"), comparable so that code can
 * branch on "2.6 or later" (FR-170).
 *
 * @author Adam
 */
public final class FirmwareVersion implements Comparable<FirmwareVersion>
{
    private static final Pattern VERSION = Pattern.compile("^\\s*v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    private final int major;
    private final int minor;
    private final int patch;

    public FirmwareVersion(int major, int minor, int patch)
    {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    /**
     * @param text what the station reports, such as "2.6.1 (Build 3)"
     * @return the version, or null if the text does not start with one
     */
    public static FirmwareVersion parse(String text)
    {
        Matcher m = text == null ? null : VERSION.matcher(text);

        try
        {
            return m == null || !m.find() ? null : new FirmwareVersion(Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)), m.group(3) == null ? 0 : Integer.parseInt(m.group(3)));
        }
        catch (NumberFormatException tooBig)
        {
            return null;
        }
    }

    /** Whether this version is the given major.minor or later. */
    public boolean atLeast(int major, int minor)
    {
        return compareTo(new FirmwareVersion(major, minor, 0)) >= 0;
    }

    @Override
    public int compareTo(FirmwareVersion other)
    {
        if (this.major != other.major) return Integer.compare(this.major, other.major);
        if (this.minor != other.minor) return Integer.compare(this.minor, other.minor);

        return Integer.compare(this.patch, other.patch);
    }

    @Override
    public boolean equals(Object other)
    {
        return other instanceof FirmwareVersion && compareTo((FirmwareVersion) other) == 0;
    }

    @Override
    public int hashCode()
    {
        return (this.major * 31 + this.minor) * 31 + this.patch;
    }

    @Override
    public String toString()
    {
        return this.major + "." + this.minor + "." + this.patch;
    }
}
