package marklin;

import base.Accessory;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Representation of each layout component as defined by CS2
 * Contains initial data and references, no actual state
 * @author Adam
 */
public class MarklinLayoutComponent
{
    public static enum componentType {CURVE, STRAIGHT, SWITCH_LEFT, 
        DOUBLE_CURVE, SWITCH_RIGHT, SWITCH_THREE, SIGNAL, UNCOUPLER, CROSSING,
        SWITCH_CROSSING, OVERPASS, FEEDBACK_CURVE, FEEDBACK, END, TUNNEL, 
        TURNTABLE, LAMP, SWITCH_Y, ROUTE, TEXT};
    
    private final String resourcePath = "/gui/resources/icons";
    
    // Rotation of the component
    private int orientation;
    
    // Coordinates
    private int x;
    private int y;
    
    // State
    private final int state;
    private final int address;
    private final int rawAddress;
    private String label= "";
    
    // Type
    private componentType type;
    
    // Accessory references
    private MarklinAccessory accessory;
    private MarklinAccessory accessory2;
    private MarklinFeedback feedback;
    private MarklinRoute route;
    
    /**
     * Constructor
     * @param type
     * @param x
     * @param y
     * @param orientation
     * @param state
     * @param address
     * @param rawAddress
     * @throws IOException 
     */
    public MarklinLayoutComponent(componentType type, int x, int y, 
            int orientation, int state, int address, int rawAddress) throws IOException
    {
        // Sanity checks
        assert x >= 0;
        assert x < 256;
        assert y >= 0;
        assert y < 256;
        assert orientation >= 0;
        assert orientation < 4;
        assert type != null;
        
        // Set state
        // We don't rely on this for anything other than initialization
        this.type = type;
        this.x = x;
        this.y = y;
        this.orientation = orientation;   
        this.state = state;
        this.address = address;
        this.rawAddress = rawAddress;
    }
    
    /**
     * Executes a switch upon user request
     */
    public void execSwitching()
    {        
        if (this.isSignal())
        {
            this.accessory.doSwitch();
        }
        else if (this.isUncoupler())
        {
            if (this.getRawAddress() % 2 == 0)
            {
                this.accessory.setSwitched(true);
            }
            else
            {
                this.accessory.setSwitched(false);
            }
        }
        else if (this.isSwitch() && ! this.isThreeWay())
        {
            this.accessory.doSwitch();
        }
        else if (this.isThreeWay())
        {
            if (this.accessory.isStraight() && this.accessory2.isStraight())
            {
                this.accessory2.setSwitched(false);
                this.accessory.delay(MarklinAccessory.THREEWAY_DELAY_MS).setSwitched(true);
            }
            else
            {
                if (this.accessory2.isStraight())
                {
                    this.accessory.setSwitched(false);
                    this.accessory2.delay(MarklinAccessory.THREEWAY_DELAY_MS).setSwitched(true);
                }
                else
                {
                    this.accessory.setSwitched(false);
                    this.accessory2.delay(MarklinAccessory.THREEWAY_DELAY_MS).setSwitched(false);
                }                      
            }  
        }
        else if (this.isFeedback())
        {
            if (this.feedback.isSet())
            {
                this.feedback.setState(false);
            }
            else
            {
                this.feedback.setState(true);
            }
        }
        else if (this.isRoute())
        {
            if (this.route != null)
            {
                this.route.execRoute();
            }
        }
    }
    
    public void setLabel(String label)
    {
        this.label = label;
    }
    
    public String getLabel()
    {
        return this.label;
    }
    
    public boolean isText()
    {
        return this.type == componentType.TEXT;
    }
    
    public boolean isRoute()
    {
        return this.type == componentType.ROUTE;
    }
    
    public boolean isSwitch()
    {
        return 
                this.type == componentType.SWITCH_LEFT ||
                this.type == componentType.SWITCH_RIGHT ||
                this.type == componentType.SWITCH_CROSSING ||
                this.type == componentType.SWITCH_THREE ||
                this.type == componentType.SWITCH_Y;
    }   
    
    public boolean isUncoupler()
    {
        return this.type == componentType.UNCOUPLER;
    }
    
    public boolean isSignal()
    {
        return this.type == componentType.SIGNAL ||
                this.type == componentType.LAMP;
    }

    public boolean isFeedback()
    {
        return this.type == componentType.FEEDBACK 
                || this.type == componentType.FEEDBACK_CURVE;
    }
    
    public boolean isThreeWay()
    {
        return this.type == componentType.SWITCH_THREE;
    }
    
    /**
     * Generates a buffered image for rotation
     * @param img
     * @return 
     */
    public static BufferedImage toBufferedImage(Image img)
    {
        if (img instanceof BufferedImage)
        {
            return (BufferedImage) img;
        }

        // Create a buffered image with transparency
        BufferedImage bimage = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);

        // Draw the image on to the buffered image
        Graphics2D bGr = bimage.createGraphics();
        bGr.drawImage(img, 0, 0, null);
        bGr.dispose();

        // Return the buffered image
        return bimage;
    }
    
    /**
     * Gets the image corresponding to the current state of the accessory
     * @param size
     * @return 
     */
    public String getImageName(int size)
    {
        String stateString = "";
                        
        if (this.isSwitch() || this.isSignal())
        {
            if (this.isThreeWay())
            {
                if (this.getAccessory().isSwitched())
                {
                    stateString = "_active";
                }
                else if (this.getAccessory2().isSwitched())
                {
                    stateString = "_active2";
                }
            }
            else
            {      
                if (this.getAccessory().isSwitched())
                {
                    stateString = "_active";
                }
            }            
        }
        
        if (this.isFeedback())
        {
            if (this.getFeedback().isSet())
            {
                stateString = "_active";
            }
        }
            
        // TODO - check if folder exists, else use a default
        // TODO - switch to /gbsicons/ 
        return this.resourcePath + Integer.toString(size) + "/" + this.getTypeName() + stateString + ".gif";
    }
    
    public Image getImage(int size) throws IOException
    {  
         Image img = ImageIO.read(MarklinLayoutComponent.class.getResource(getImageName(size)));
         
         // Resize only if we don't have the right icon
         if (size != img.getWidth(null))
         {
            float aspect = (float) img.getHeight(null) / (float) img.getWidth(null);

            img = img.getScaledInstance(size, (int) (size * aspect), 1);
         }
         
         // Rotate
         if (this.orientation > 0)
         {
            AffineTransform transform = new AffineTransform();
            transform.rotate(Math.toRadians ((4 - this.orientation) * 90), img.getWidth(null)/2, img.getHeight(null)/2);
            AffineTransformOp op = new AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR);
            return op.filter(toBufferedImage(img), null);
         }
         
         return img;
    }
    
    /**
     * Currently used to determine the icon name
     * @return 
     */
    public String getTypeName()
    {
        switch(this.type)
        {
            case UNCOUPLER:
                return "decouple";
            case END:
                return "end";                
            case FEEDBACK:
                return "s88";
            case FEEDBACK_CURVE:
                return "s88_curve";
            case STRAIGHT:
                return "straight";
            case SIGNAL:
                return "signal";
            case DOUBLE_CURVE:
                return "curve_parallel";
            case CURVE:
                return "curve";
            case SWITCH_LEFT:
                return "switch_left";
            case SWITCH_RIGHT:
                return "switch_right";
            case SWITCH_THREE:
                return "threeway";
            case TUNNEL:
                return "tunnel";
            case CROSSING:
                return "cross";
            case OVERPASS:
                return "overpass";
            case SWITCH_CROSSING:
                return "crossswitch";
            case TURNTABLE:
                return "turntable";
            case LAMP:
                return "lamp";
            case SWITCH_Y:
                return "switch_y";
            case ROUTE:
                return "route";
        }
        
        return "";
    }

    // Boring getters and setters
   
    /**
     * The low-level address, i.e. separate addresses for red and green
     * @return 
     */
    public int getRawAddress()
    {
        return rawAddress;
    }
    
    public MarklinAccessory getAccessory()
    {
        return accessory;
    }

    public MarklinAccessory getAccessory2()
    {
        return accessory2;
    }

    public MarklinFeedback getFeedback()
    {
        return feedback;
    }
    
    public MarklinRoute getRoute()
    {
        return route;
    }
    
     public int getOrientation()
    {
        return orientation;
    }

    public int getX()
    {
        return x;
    }

    public int getY()
    {
        return y;
    }
    
    public componentType getType()
    {
        return type;
    }

    public void setOrientation(int orientation)
    {
        this.orientation = orientation;
    }

    public void setX(int x)
    {
        this.x = x;
    }

    public void setY(int y)
    {
        this.y = y;
    }

    public void setType(componentType type)
    {
        this.type = type;
    }
    
    public void setRoute(MarklinRoute route)
    {
        this.route = route;
    }
    
    public void setAccessory(MarklinAccessory accessory)
    {
        // Type can be wrong if a switch is changed to a signal in the CS2
        // Solution: reboot CS2 :), or update the state here
        //if (this.isSignal() && !accessory.isSignal())
        //{
        
        //}
        
        this.accessory = accessory;
    }

    public void setAccessory2(MarklinAccessory accessory2)
    {
        this.accessory2 = accessory2;
    }

    public void setFeedback(MarklinFeedback feedback)
    {
        this.feedback = feedback;
    }
    
    public int getAddress()
    {
        return this.address;
    }
    
    public int getState()
    {
        return this.state;
    }
    
    @Override
    public String toString()
    {        
        if (this.accessory != null)
        {
            if (this.accessory2 != null)
            {
                return this.accessory.toString() + this.accessory2.toString();
            }
            
            return this.accessory.toString();
        }
        else if (this.feedback != null)
        {
            return this.feedback.toString();
        }
        else 
        {
            return this.getTypeName() + " (" + 
                Integer.toString(this.x) + "," +
                Integer.toString(this.y) + ") " + 
                Integer.toString(this.orientation * 90) + " deg " +
                "#" + Integer.toString(this.address)
            ;
        }
    }
}
