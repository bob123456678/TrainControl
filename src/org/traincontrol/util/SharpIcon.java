package org.traincontrol.util;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.AffineTransform;
import java.util.Map;
import java.util.function.Function;
import javax.swing.ImageIcon;

/**
 * An ImageIcon that a scaled screen draws sharp (D191): where Java 9 and later scale the drawing, it paints a picture
 * made once at the screen's real pixels from finer art, rather than stretching its own.  At scale 1 (and always on
 * Java 8) it paints exactly as an ImageIcon, and `getImage` is always the picture at scale 1.
 */
public class SharpIcon extends ImageIcon
{
    /** Makes the picture at a size in screen pixels. */
    public interface Art
    {
        Image at(int width, int height) throws Exception;
    }

    private final Art art;
    private final Map<Dimension, Image> made = new java.util.concurrent.ConcurrentHashMap<>();

    public SharpIcon(Image picture, Art art)
    {
        super(picture);
        this.art = art;
    }

    /**
     * `picture`, drawn on a scaled screen from `finer` resampled to the screen's pixels once loaded: area-averaged, or
     * bilinear where `finer` is the smaller, which area-averaging would make blocky.
     */
    public SharpIcon(Image picture, Image finer)
    {
        this(picture, (w, h) -> new ImageIcon(finer).getIconWidth() < w
            ? ImageUtil.getScaledImage(ImageUtil.toTransparentBufferedImage(finer), w, h)
            : ImageUtil.toTransparentBufferedImage(
                new ImageIcon(finer.getScaledInstance(w, h, Image.SCALE_SMOOTH)).getImage()));
    }

    /** The scale the screen showing `c` draws at, the default screen's without one: 1 on Java 8 and with no screen. */
    public static double screenScale(Component c)
    {
        try
        {
            java.awt.GraphicsConfiguration on = c == null ? null : c.getGraphicsConfiguration();
            return (on != null ? on : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration()).getDefaultTransform().getScaleX();
        }
        catch (java.awt.HeadlessException noScreen)
        {
            return 1;
        }
    }

    /** `icon` with `change` made to its picture, and to each sharp copy if it is a SharpIcon. */
    public static ImageIcon map(ImageIcon icon, Function<Image, Image> change)
    {
        Image picture = change.apply(icon.getImage());
        SharpIcon from = icon instanceof SharpIcon ? (SharpIcon) icon : null;
        return from == null ? new ImageIcon(picture) : new SharpIcon(picture, (w, h) -> change.apply(from.at(w, h)));
    }

    /** The picture at a size in screen pixels, made once; null if it cannot be made. */
    private Image at(int width, int height)
    {
        return made.computeIfAbsent(new Dimension(width, height), d ->
        {
            try
            {
                return art.at(d.width, d.height);
            }
            catch (Exception noArt)
            {
                return null;
            }
        });
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y)
    {
        AffineTransform t = g instanceof Graphics2D ? ((Graphics2D) g).getTransform() : new AffineTransform();
        // The screen pixels whose centers the icon covers, as Java's clip counts them, so squares side by side meet
        int left = (int) Math.ceil(t.getScaleX() * x + t.getTranslateX() - 0.5);
        int top = (int) Math.ceil(t.getScaleY() * y + t.getTranslateY() - 0.5);
        int width = (int) Math.ceil(t.getScaleX() * (x + getIconWidth()) + t.getTranslateX() - 0.5) - left;
        int height = (int) Math.ceil(t.getScaleY() * (y + getIconHeight()) + t.getTranslateY() - 0.5) - top;
        Image sharp = t.getScaleX() > 1 && t.getScaleY() > 1 && t.getShearX() == 0 && t.getShearY() == 0
            && width > 0 && height > 0 ? at(width, height) : null;
        if (sharp == null)
        {
            super.paintIcon(c, g, x, y);
            return;
        }

        Graphics2D screen = (Graphics2D) g.create();
        try
        {
            screen.setTransform(new AffineTransform());
            screen.drawImage(sharp, left, top, null);
        }
        finally
        {
            screen.dispose();
        }
    }
}
