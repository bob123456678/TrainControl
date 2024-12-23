package org.traincontrol.util;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import javax.swing.ImageIcon;

/**
 * Image manipulation utility functions
 */
public class ImageUtil
{
    
    public static final HashMap<RenderingHints.Key, Object> RenderingProperties = new HashMap<>();

    static
    {
        RenderingProperties.put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        RenderingProperties.put(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        RenderingProperties.put(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    /**
     * https://stackoverflow.com/questions/18800717/convert-text-content-to-image
     * @param Text
     * @param f
     * @param Size
     * @param background
     * @param paddingTop
     * @return 
     */
    public static BufferedImage textToImage(String Text, Font f, float Size, Color background, int paddingTop)
    {
        //Derives font to new specified size, can be removed if not necessary.
        f = f.deriveFont(Size);

        FontRenderContext frc = new FontRenderContext(null, true, true);

        //Calculate size of buffered image.
        LineMetrics lm = f.getLineMetrics(Text, frc);

        Rectangle2D r2d = f.getStringBounds(Text, frc);

        BufferedImage img = new BufferedImage((int)Math.ceil(r2d.getWidth()), (int)Math.ceil(r2d.getHeight() + paddingTop), BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2d = img.createGraphics();

        g2d.setRenderingHints(RenderingProperties);

        g2d.setBackground(background);
        g2d.setColor(Color.BLACK);

        g2d.clearRect(0, 0, img.getWidth(), img.getHeight());

        g2d.setFont(f);

        g2d.drawString(Text, 0, lm.getAscent() + paddingTop);

        g2d.dispose();

        return img;
    }
    
    /**
     * https://stackoverflow.com/questions/37758061/rotate-a-buffered-image-in-java
     * @param buffImage
     * @param angle
     * @return 
     */
    public static BufferedImage rotateImage(BufferedImage buffImage, double angle)
    {
        double radian = Math.toRadians(angle);
        double sin = Math.abs(Math.sin(radian));
        double cos = Math.abs(Math.cos(radian));

        int width = buffImage.getWidth();
        int height = buffImage.getHeight();

        int nWidth = (int) Math.floor((double) width * cos + (double) height * sin);
        int nHeight = (int) Math.floor((double) height * cos + (double) width * sin);

        BufferedImage rotatedImage = new BufferedImage(
                nWidth, nHeight, BufferedImage.TYPE_INT_ARGB);

        Graphics2D graphics = rotatedImage.createGraphics();

        graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        graphics.translate((nWidth - width) / 2, (nHeight - height) / 2);
        // rotation around the center point
        graphics.rotate(radian, (double) (width / 2), (double) (height / 2));
        graphics.drawImage(buffImage, 0, 0, null);
        graphics.dispose();

        return rotatedImage;
    }
    
    /**
     * https://stackoverflow.com/questions/15975610/java-image-scaling-improve-quality
     * @param image
     * @param width
     * @param height
     * @return 
     */
    public static BufferedImage getScaledImage(BufferedImage image, int width, int height)
    {
        int imageWidth  = image.getWidth();
        int imageHeight = image.getHeight();

        double scaleX = (double)width/imageWidth;
        double scaleY = (double)height/imageHeight;
        AffineTransform scaleTransform = AffineTransform.getScaleInstance(scaleX, scaleY);
        AffineTransformOp bilinearScaleOp = new AffineTransformOp(scaleTransform, AffineTransformOp.TYPE_BILINEAR);

        return bilinearScaleOp.filter(
            image,
            new BufferedImage(width, height, image.getType()));
    }
    
    /**
    * Converts a given Image into a BufferedImage
    * https://stackoverflow.com/questions/13605248/java-converting-image-to-bufferedimage
    * @param img The Image to be converted
    * @return The converted BufferedImage
    */
    public static BufferedImage toTransparentBufferedImage(Image img)
    {
        /*if (img instanceof BufferedImage)
        {
            return (BufferedImage) img;
        }*/

        // Create a buffered image with transparency
        BufferedImage bimage = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);

        // Draw the image on to the buffered image
        Graphics2D bGr = bimage.createGraphics();
        bGr.setColor(new Color(0,0,0,0));
        bGr.fillRect(0, 0, img.getWidth(null), img.getHeight(null));
        bGr.drawImage(img, 0, 0, null);
        bGr.dispose();

        // Return the buffered image
        return bimage;
    }
    
    /**
     * Generates an image with a rectangle matching the size of the string
     * @param text
     * @param textColor
     * @param font
     * @param width
     * @param height
     * @param xOffset
     * @param yOffset
     * @param widthAdjust
     * @param heightAdjust
     * @return 
     */
    public static BufferedImage generateImageWithRect(String text, Color textColor, Font font, int width, int height, int xOffset, int yOffset,
            int widthAdjust, int heightAdjust)
    {
        // Create a BufferedImage with transparency
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2d = image.createGraphics();

        // Set the background to be transparent
        g2d.setComposite(AlphaComposite.Clear);
        g2d.fillRect(0, 0, width, height);

        g2d.setComposite(AlphaComposite.Src);
        g2d.setColor(textColor);
        g2d.setFont(font);

        // Calculate the position to center the text
        FontMetrics fontMetrics = g2d.getFontMetrics();
        int x = (width - fontMetrics.stringWidth(text)) / 2 + xOffset;
        int y = (height - fontMetrics.getHeight()) / 2 + fontMetrics.getAscent() + yOffset;

        g2d.fillRect(x, y - fontMetrics.getAscent(), fontMetrics.stringWidth(text) + widthAdjust, fontMetrics.getHeight() + heightAdjust);

        g2d.dispose();

        return image;
    }
    
    /**
     * Generates an image with text with the passed font and offset relative to the center of the canvas
     * @param text
     * @param textColor
     * @param font
     * @param width
     * @param height
     * @param xOffset
     * @param yOffset
     * @return 
     */
    public static BufferedImage generateImageWithText(String text, Color textColor, Font font, int width, int height, int xOffset, int yOffset)
    {
        // Create a BufferedImage with transparency
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2d = image.createGraphics();

        // Set the background to be transparent
        g2d.setComposite(AlphaComposite.Clear);
        g2d.fillRect(0, 0, width, height);

        g2d.setComposite(AlphaComposite.Src);
        g2d.setColor(textColor);
        g2d.setFont(font);

        // Calculate the position to center the text
        FontMetrics fontMetrics = g2d.getFontMetrics();
        int x = (width - fontMetrics.stringWidth(text)) / 2 + xOffset;
        int y = (height - fontMetrics.getHeight()) / 2 + fontMetrics.getAscent() + yOffset;

        g2d.drawString(text, x, y);

        g2d.dispose();

        return image;
    }
    
    /**
     * Combines two images
     * @param image1
     * @param image2
     * @return 
     */
    public static BufferedImage mergeImages(BufferedImage image1, BufferedImage image2)
    {
        BufferedImage mergedImage = new BufferedImage(image1.getWidth(), image1.getHeight(), BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2d = mergedImage.createGraphics();

        g2d.drawImage(image1, 0, 0, null);

        //g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
        g2d.drawImage(image2, 0, 0, null);

        g2d.dispose();

        return mergedImage;
    }

    public static BufferedImage convertIconToBufferedImage(ImageIcon icon)
    {
        // Extract the image from the icon
        Image image = icon.getImage();

        // Create a buffered image with transparency
        BufferedImage bufferedImage = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);

        // Draw the image onto the buffered image
        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();

        return bufferedImage;
    }
    
    /**
     * Highlights an icon
     * @param originalIcon
     * @return 
     */
    public static ImageIcon addHighlightOverlay(ImageIcon originalIcon)
    {
        int width = originalIcon.getIconWidth();
        int height = originalIcon.getIconHeight();

        BufferedImage highlightedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = highlightedImage.createGraphics();

        // Draw the original image
        g2d.drawImage(originalIcon.getImage(), 0, 0, null);

        // Draw the yellow overlay
        g2d.setColor(new Color(255, 255, 0, 128)); // Semi-transparent yellow
        g2d.fillRect(0, 0, width, height);

        g2d.dispose();

        return new ImageIcon(highlightedImage);
    }
}
