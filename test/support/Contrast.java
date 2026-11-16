package support;

import java.awt.Color;

/** The WCAG contrast ratio of two colors, for claims that text can be read on its fill. */
public final class Contrast
{
    private Contrast()
    {
    }

    /** @return the ratio of the lighter to the darker relative luminance, each plus 0.05: 1 to 21 */
    public static double between(Color text, Color fill)
    {
        double a = luminance(text);
        double b = luminance(fill);

        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(Color c)
    {
        return 0.2126 * channel(c.getRed()) + 0.7152 * channel(c.getGreen()) + 0.0722 * channel(c.getBlue());
    }

    private static double channel(int value)
    {
        double v = value / 255.0;

        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }
}
