package org.traincontrol.base;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.imageio.ImageIO;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.util.I18n;

/**
 * Representation of each layout component as defined by CS2
 * Contains initial data and references, no actual state
 * @author Adam
 */
public class LayoutDiagramComponent
{
    public static enum componentType {
        STRAIGHT, CURVE, DOUBLE_CURVE, 
        SIGNAL, END, SWITCH_Y, 
        SWITCH_LEFT, SWITCH_RIGHT, SWITCH_THREE,  
        SWITCH_CROSSING, OVERPASS, CROSSING , 
        FEEDBACK, FEEDBACK_CURVE, FEEDBACK_DOUBLE_CURVE, 
        UNCOUPLER, TUNNEL, TURNTABLE, 
        LAMP, ROUTE, LINK, 
        CUSTOM_PERM_LEFT, CUSTOM_PERM_RIGHT, CUSTOM_PERM_Y, 
        CUSTOM_PERM_THREEWAY, CUSTOM_PERM_SCISSORS, CUSTOM_SCISSORS, 
        TEXT
    };
        
    private final static String RESOURCE_PATH = "/org/traincontrol/gui/resources/icons";
    
    // Rotation of the component
    private int orientation;
    
    // Coordinates
    private int x;
    private int y;
    
    // State
    private final int state;
    private int address;
    private int rawAddress;
    private String label = "";
    private accessoryDecoderType protocol;
    
    // Type
    private componentType type;
    
    // Accessory references
    private Accessory accessory;
    private Accessory accessory2;
    private Feedback feedback;
    private Route route;
    
    /**
     * Constructor
     * @param type
     * @param x
     * @param y
     * @param orientation
     * @param state
     * @param address
     * @param rawAddress
     * @param protocol
     * @throws IOException 
     */
    public LayoutDiagramComponent(componentType type, int x, int y, 
            int orientation, int state, int address, int rawAddress, accessoryDecoderType protocol) throws IOException
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
        this.protocol = protocol;
    }
    
    /**
     * Copy constructor for MarklinLayoutComponent. Copies all base fields, including the text label.
     * Used by layout editor
     * 
     * @param original The original MarklinLayoutComponent to copy.
     * @throws java.io.IOException
     */
    public LayoutDiagramComponent(LayoutDiagramComponent original) throws IOException
    {
        this(original.type, original.x, original.y, original.orientation, original.state, original.address, original.rawAddress, original.protocol);
        
        this.label = original.label;
    }
    
    /**
     * Executes a switch upon user request
     */
    public void execSwitching()
    {        
        if (this.isSignal() && this.accessory != null)
        {
            this.accessory.doSwitch();
        }
        else if (this.isUncoupler() && this.accessory != null)
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
        else if (this.isSwitch() && ! this.isThreeWay() && this.accessory != null)
        {
            this.accessory.doSwitch();
        }
        else if (this.isThreeWay() && this.accessory != null && this.accessory2 != null)
        {
            if (this.accessory.isStraight() && this.accessory2.isStraight())
            {
                this.accessory2.setSwitched(false);
                this.accessory.delay(this.accessory.getThreeWaySwitchingDelay()).setSwitched(true);
            }
            else
            {
                if (this.accessory2.isStraight())
                {
                    this.accessory.setSwitched(false);
                    this.accessory2.delay(this.accessory2.getThreeWaySwitchingDelay()).setSwitched(true);
                }
                else
                {
                    this.accessory.setSwitched(false);
                    this.accessory2.delay(this.accessory2.getThreeWaySwitchingDelay()).setSwitched(false);
                }                      
            }  
        }
        else if (this.isFeedback() && this.feedback != null)
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
                this.route.execRoute(false);
            }
        }
        
        // This should never be reached
    }
    
    public void setLabel(String label)
    {
        this.label = label;
    }
    
    public boolean hasLabel()
    {
        return this.label != null && !"".equals(label);
    }
    
    public boolean isClickable()
    {
        return this.isRoute() || this.isSignal() || this.isSwitch() || this.isUncoupler() || this.isFeedback()
                || this.isLamp() || this.isLink();
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
                this.type == componentType.SWITCH_Y ||
                this.type == componentType.CUSTOM_SCISSORS;
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
    
    public boolean isLamp()
    {
        return this.type == componentType.LAMP;
    }

    public boolean isFeedback()
    {
        return this.type == componentType.FEEDBACK 
                || this.type == componentType.FEEDBACK_CURVE
                || this.type == componentType.FEEDBACK_DOUBLE_CURVE;
    }
    
    public boolean isThreeWay()
    {
        return this.type == componentType.SWITCH_THREE;
    }

    /**
     * The state the accessory at getAddress() should be recorded as when this component is
     * imported into the accessory database.
     *
     * A three-way's two drives have exactly three combinations it can hold - straight with both
     * released, left with the first thrown, right with the second - which is the cycle
     * execSwitching walks.  Seeding from state != 1 made state 2 both drives thrown, which is
     * none of the three, so the turnout opened in a position it cannot physically be in.
     *
     * @return true if the first drive should be recorded as thrown
     */
    public boolean getPrimaryDriveState()
    {
        return this.isThreeWay() ? this.state == 0 : this.state != 1;
    }

    /**
     * The state the accessory at getAddress() + 1 should be recorded as.  Three-ways only -
     * nothing else has a second drive.
     *
     * @return true if the second drive should be recorded as thrown
     */
    public boolean getSecondaryDriveState()
    {
        return this.state == 2;
    }
    
    public boolean isLink()
    {
        return this.type == componentType.LINK;
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
        
        if (img != null)
        {
            // Create a buffered image with transparency
            BufferedImage bimage = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);

            // Draw the image on to the buffered image
            Graphics2D bGr = bimage.createGraphics();
            bGr.drawImage(img, 0, 0, null);
            bGr.dispose();

            // Return the buffered image
            return bimage;
        }
        
        return null;
    }
    
    /**
     * Gets the image corresponding to the current state of the accessory
     * @param size
     * @param ignoreState true to never display the active icon (useful for editing)
     * @return 
     */
    public String getImageName(int size, boolean ignoreState)
    {        
        String stateString = "";
        
        if (!ignoreState)
        {                     
            if (this.isSwitch() || this.isSignal())
            {
                if (this.isThreeWay() && this.getAccessory() != null && this.getAccessory2() != null)
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
                else if (this.getAccessory() != null)
                {      
                    if (this.getAccessory().isSwitched())
                    {
                        stateString = "_active";
                    }
                }            
            }

            if (this.isFeedback())
            {
                if (this.getFeedback() != null && this.getFeedback().isSet())
                {
                    stateString = "_active";
                }
            }

            if (this.isRoute())
            {
                if (this.getRoute() != null && this.getRoute().isExecuting())
                {
                    stateString = "_active";
                }
            }
        }
                    
        // TODO - check if folder exists, else use a default
        // TODO - switch to /gbsicons/ 
        return RESOURCE_PATH + Integer.toString(size) + "/" + this.getTypeName() + stateString + ".gif";
    }
    
    public Image getImage(int size, boolean ignoreState) throws IOException
    {  
         String imageName = getImageName(size, ignoreState);
         java.net.URL icon = LayoutDiagramComponent.class.getResource(imageName);

         if (icon == null)
         {
             throw new IOException(I18n.f("error.missingLayoutIcon", imageName));
         }

         Image img = ImageIO.read(icon);

         if (img == null)
         {
             throw new IOException(I18n.f("error.missingLayoutIcon", imageName));
         }

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
     * The name of the icon file used for this component
     * @return 
     */
    public String getTypeName()
    {
        switch (this.type)
        {
            case UNCOUPLER:
                return "decouple";
            case END:
                return "end";                
            case FEEDBACK:
                return "s88";
            case FEEDBACK_CURVE:
                return "s88_curve";
            case FEEDBACK_DOUBLE_CURVE:
                return "s88_double_curve";
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
            case LINK:
                return "link";
            // Custom components - filename is the same but in lowercase
            case CUSTOM_PERM_LEFT:
            case CUSTOM_PERM_RIGHT:
            case CUSTOM_PERM_Y:
            case CUSTOM_PERM_THREEWAY:
            case CUSTOM_PERM_SCISSORS:
            case CUSTOM_SCISSORS:
                return this.type.toString().toLowerCase();
        }
        
        return "";
    }
    
    /**
     * A user-friendly description of the component
     * @return 
     */
    public String getUserFriendlyTypeName()
    {
        switch (this.type)
        {
            case UNCOUPLER:
                return I18n.t("layout.uncoupler");
            case END:
                return I18n.t("layout.bumper");
            case FEEDBACK:
                return I18n.t("layout.s88Feedback");
            case FEEDBACK_CURVE:
                return I18n.t("layout.s88FeedbackCurved");
            case FEEDBACK_DOUBLE_CURVE:
                return I18n.t("layout.s88FeedbackParallel");
            case STRAIGHT:
                return I18n.t("layout.straightTrack");
            case SIGNAL:
                return I18n.t("layout.signal");
            case DOUBLE_CURVE:
                return I18n.t("layout.parallelTrack");
            case CURVE:
                return I18n.t("layout.curvedTrack");
            case SWITCH_LEFT:
                return I18n.t("layout.leftSwitch");
            case SWITCH_RIGHT:
                return I18n.t("layout.rightSwitch");
            case SWITCH_THREE:
                return I18n.t("layout.threeWaySwitch");
            case TUNNEL:
                return I18n.t("layout.tunnel");
            case CROSSING:
                return I18n.t("layout.crossing");
            case OVERPASS:
                return I18n.t("layout.overpass");
            case SWITCH_CROSSING:
                return I18n.t("layout.doubleSlipSwitch");
            case TURNTABLE:
                return I18n.t("layout.turntable");
            case LAMP:
                return I18n.t("layout.lampAccessory");
            case SWITCH_Y:
                return I18n.t("layout.ySwitch");
            case ROUTE:
                return I18n.t("layout.routeShortcut");
            case LINK:
                return I18n.t("layout.pageLink");
            case CUSTOM_PERM_LEFT:
                return I18n.t("layout.leftSwitchStatic");
            case CUSTOM_PERM_RIGHT:
                return I18n.t("layout.rightSwitchStatic");
            case CUSTOM_PERM_Y:
                return I18n.t("layout.ySwitchStatic");
            case CUSTOM_PERM_THREEWAY:
                return I18n.t("layout.threeWaySwitchStatic");
            case CUSTOM_PERM_SCISSORS:
                return I18n.t("layout.scissorSwitchStatic");
            case CUSTOM_SCISSORS:
                return I18n.t("layout.scissorSwitch");
            case TEXT:
                return I18n.t("layout.textLabel");
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
    
    public Accessory getAccessory()
    {
        return accessory;
    }

    public Accessory getAccessory2()
    {
        return accessory2;
    }

    public Feedback getFeedback()
    {
        return feedback;
    }
    
    public Route getRoute()
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
    
    public void setRoute(Route route)
    {
        this.route = route;
    }
    
    public void setAccessory(Accessory accessory)
    {
        this.accessory = accessory;
    }

    public void setAccessory2(Accessory accessory2)
    {
        this.accessory2 = accessory2;
    }

    public void setFeedback(Feedback feedback)
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
    
    /**
     * Basic description for the UI
     * @return 
     */
    public String toSimpleString()
    {        
        // Add the protocol
        String digitalProtocol = "";

        if (this.getAccessory() != null)
        {
            digitalProtocol = Accessory.getProtocolStringForName(this.getAccessory().getDecoderType().toString());
        }
        
        if (this.isThreeWay())
        {
            return I18n.f("layout.switchThreeWayAddr", this.getAddress(), this.getAddress() + 1, digitalProtocol);
        }
        else if (this.isSwitch())
        {
            return I18n.f("layout.switchAddr", this.getAddress(), digitalProtocol);
        }
        else if (this.isUncoupler())
        {
            return I18n.f("layout.uncouplerColored", this.getAddress(),
                (this.getRawAddress() % 2 == 0 ? I18n.t("layout.red") : I18n.t("layout.green")),
                digitalProtocol);
        }
        else if (this.isFeedback())
        {
            return I18n.f("layout.feedbackUid", this.getFeedback().getUID());
        }
        else if (this.isSignal())
        {
            return I18n.f(this.isLamp() ? "layout.accessoryAddr" : "layout.signalAddr", this.getAddress(), digitalProtocol);
        }
        else if (this.isRoute() && this.getRoute() != null)
        {
            return I18n.f("layout.route", this.getRoute().getId(), this.getRoute().getName());
        }
        else if (this.isLink())
        {
            return I18n.f("layout.linkPage", this.getRawAddress() + 1);
        }
        else
        {
            return "";
        }
    }
    
    /**
     * Returns a unique string for this component's image, suitable for caching
     * @param size
     * @param ignoreState
     * @return 
     */
    public String getImageKey(int size, boolean ignoreState)
    {
        return this.getImageName(size, ignoreState) + "_" + Integer.toString(orientation);
    } 
    
    /**
     * Everything the file said about this square that this class has no field for.
     *
     * Kept only so that it can be written back.  Saving a layout regenerates the whole file from this
     * model, so any key the model does not carry was quietly deleted from the user's diagram - and
     * saving is not always something they asked for: naming a station writes the page out.  What
     * TrainControl does not understand it is not entitled to throw away.
     */
    private Map<String, String> unmodelledKeys = null;

    /**
     * The word the file used for this component, or null for one this program created.
     *
     * Kept for the same reason unmodelledKeys are: saving regenerates the page, and what the model
     * cannot express it must not silently replace.  See exportToCS2TextFormat.
     */
    private String originalTyp;

    /**
     * The rotation exactly as the file gave it, before any correction this program applies on the way
     * in.  Written back unchanged while the orientation has not been edited, so a correction cannot be
     * applied twice - once when read and once more when read again after a save.
     */
    private Integer originalDrehung;

    /**
     * @param typ the file's own type word
     * @param drehung the file's own rotation, or null if it gave none
     */
    public void setOriginalFileForm(String typ, Integer drehung)
    {
        this.originalTyp = typ;
        this.originalDrehung = drehung;
    }

    /**
     * Whether the given file word still maps to this component's type.  Set by the parser via
     * setOriginalFileForm; null when nothing was read, in which case the canonical word is used.
     */
    private componentType getComponentTypeOf(String typ)
    {
        return typeOfFileWord == null ? null : typeOfFileWord.apply(typ);
    }

    /**
     * How to turn a file word back into a type - supplied by the parser, which owns that mapping.
     */
    private static java.util.function.Function<String, componentType> typeOfFileWord;

    /**
     * @param mapping the parser's own typ-to-type function, so this class need not duplicate it
     */
    public static void setFileWordMapping(java.util.function.Function<String, componentType> mapping)
    {
        typeOfFileWord = mapping;
    }

    /**
     * @param keys the file's keys for this element, from which the ones this class models are dropped
     */
    public void setUnmodelledKeys(Map<String, String> keys)
    {
        if (keys == null || keys.isEmpty())
        {
            this.unmodelledKeys = null;
            return;
        }

        Map<String, String> kept = new TreeMap<>(keys);

        for (String known : MODELLED_KEYS) kept.remove(known);

        this.unmodelledKeys = kept.isEmpty() ? null : kept;
    }

    /**
     * The keys this class reads out of a CS2 element and can therefore write back on its own.
     */
    private static final List<String> MODELLED_KEYS = Arrays.asList(
        "_type", "id", "typ", "drehung", "prot", "artikel", "zustand", "text");

    /**
     * Exports this component in the CS2 file format
     * Limited to the component types supported in TrainControl
     * @return
     * @throws Exception
     */
    public String exportToCS2TextFormat() throws Exception
    {
        StringBuilder builder = new StringBuilder();

        // Empty text labels aren't saved.  The null check belongs inside the TEXT test, guarding the
        // isEmpty() call - as written before, && bound tighter than ||, so a null label dropped a
        // component of ANY type from the export.  Unreachable today (the field starts as "" and both
        // setLabel callers guard against null), but the guard did not mean what its comment says.
        // An empty text square is nothing, and is not written - unless the file said something about it
        // that this class has no field for, in which case dropping it would delete that too.
        if (this.type == componentType.TEXT && (this.label == null || this.label.isEmpty())
            && this.unmodelledKeys == null) return "";
        
        // Add "element"
        builder.append("element\n");

        // Add .id (format it back to hex)
        if (this.x != 0 || this.y != 0)
        {
            builder.append(" .id=0x").append(String.format("%x", this.x + (this.y << 8))).append("\n");
        }
        
        // Add .typ - the file's own word for it wherever this component still IS what was read.
        //
        // getComponentType is many-to-one and getTypeString is one word per type, so writing the
        // canonical name back collapsed every variant the file distinguished: fifteen signal types
        // became "signal", the four coloured lamps and the level crossing became "lampe", a CS3 double
        // slip half became an ordinary switch.  Worse for signals, the parser subtracts one from the
        // rotation of any type whose name contains "_f_" to correct the artwork - and the canonical
        // name does not, so the correction was baked in and the signal came back turned a step.
        //
        // Only while the type is unchanged.  Once the user redraws the square in the diagram editor it
        // is a different component and the canonical name is the honest one.
        if (this.type != componentType.TEXT)
        {
            builder.append(" .typ=")
                .append(originalTyp != null && getComponentTypeOf(originalTyp) == this.type
                    ? originalTyp : getTypeString(this.type))
                .append("\n");
        }
        
        // Add .drehung only if orientation is not 0 - or write back exactly what was read, where the
        // orientation has not been touched.  See the note on typ above: the parser corrects the
        // rotation of some signal types on the way in, and writing the corrected value back under a
        // name that no longer triggers the correction turns the signal a step on every round trip.
        if (originalDrehung != null && orientationMatchesFile())
        {
            builder.append(" .drehung=").append(originalDrehung.intValue()).append("\n");
        }
        else if (this.orientation != 0)
        {
            builder.append(" .drehung=").append(this.orientation).append("\n");
        }
        
        // Custom state
        if (this.protocol != null && this.protocol != Accessory.accessoryDecoderType.MM2)
        {
            builder.append(" .prot=").append(this.protocol.toString()).append("\n");
        }
        
        // Add .artikel (raw address)  
        if (this.type != componentType.TEXT)
        {
            builder.append(" .artikel=").append(this.rawAddress).append("\n");
        }
        else
        {
            builder.append(" .artikel=-1").append("\n");
        }
        
        // Add state
        if (this.state > 0)
        {
            builder.append(" .zustand=").append(this.state).append("\n");
        }

        // Add .text (label, if not empty)
        if (this.label != null && !this.label.isEmpty())
        {
            builder.append(" .text=").append(this.label).append("\n");
        }

        // And back out again, everything the file said that this class has no field for
        if (this.unmodelledKeys != null)
        {
            for (Map.Entry<String, String> extra : this.unmodelledKeys.entrySet())
            {
                appendPreservedKey(builder, extra.getKey(), extra.getValue());
            }
        }

        return builder.toString().trim();
    }
    

    /**
     * Writes one key of an unmodelled element or block back in the syntax it was read in.
     *
     * The parser folds a CS2 array - a key line followed by " ..sub=value" lines - into ONE map entry
     * holding "{a=b,c=d}|{e=f}".  Writing that back as a single " .key=..." line preserves the text and
     * loses the syntax: the Central Station, and any parser including this one, would read it as a
     * scalar whose value happens to contain braces.  Since the whole point of keeping unmodelled
     * content is that a later firmware's file survives a round trip, the one shape most likely to BE a
     * later firmware's is the one that must not be mangled.
     *
     * Unfolded back into the same shape it came from.  The parser used a HashMap for the entries, so
     * their order within a group is already lost - what is restored is the structure, not the ordering.
     *
     * @param builder where the lines go
     * @param key the key name, without its leading dot
     * @param value the value as the parser left it
     */
    static void appendPreservedKey(StringBuilder builder, String key, String value)
    {
        int brace = value == null ? -1 : value.indexOf('{');

        if (brace < 0 || !value.endsWith("}"))
        {
            builder.append(" .").append(key).append('=').append(value).append("\n");
            return;
        }

        // Whatever preceded the first group was the key's own scalar value
        String scalar = value.substring(0, brace);

        builder.append(" .").append(key);

        if (!scalar.isEmpty()) builder.append('=').append(scalar);

        builder.append("\n");

        for (String group : value.substring(brace).split("\\|"))
        {
            String inner = group.trim();

            if (inner.startsWith("{")) inner = inner.substring(1);
            if (inner.endsWith("}")) inner = inner.substring(0, inner.length() - 1);

            if (inner.isEmpty()) continue;

            for (String entry : inner.split(","))
            {
                if (!entry.isEmpty()) builder.append(" ..").append(entry).append("\n");
            }
        }
    }

    /**
     * Converts the internal type to the Marklin format
     * @param type
     * @return
     * @throws Exception 
     */
    public static String getTypeString(LayoutDiagramComponent.componentType type) throws Exception
    {
        switch (type)
        {
            case UNCOUPLER:
                return "entkuppler";
            case END:
                return "prellbock";
            case FEEDBACK:
                return "s88kontakt";
            case FEEDBACK_CURVE:
                return "s88bogen";
            case FEEDBACK_DOUBLE_CURVE:
                return "s88doppelbogen";
            case STRAIGHT:
                return "gerade";
            case SIGNAL:
                return "signal"; // Default to generic signal string
            case DOUBLE_CURVE:
                return "doppelbogen";
            case CURVE:
                return "bogen";
            case TUNNEL:
                return "tunnel";
            case CROSSING:
                return "kreuzung";
            case OVERPASS:
                return "unterfuehrung";
            case SWITCH_CROSSING:
                return "dkweiche";
            case TURNTABLE:
                return "drehscheibe";
            case LAMP:
                return "lampe";
            case ROUTE:
                return "fahrstrasse";
            case TEXT:
                return "text";
            case LINK:
                return "pfeil";
            case SWITCH_LEFT:
                return "linksweiche";
            case CUSTOM_PERM_LEFT:
                return "custom_perm_left";
            case SWITCH_RIGHT:
                return "rechtsweiche";
            case CUSTOM_PERM_RIGHT:
                return "custom_perm_right";
            case SWITCH_Y:
                return "yweiche";
            case CUSTOM_PERM_Y:
                return "custom_perm_y";
            case SWITCH_THREE:
                return "dreiwegweiche";
            case CUSTOM_PERM_THREEWAY:
                return "custom_perm_threeway";
            case CUSTOM_SCISSORS:
                return "hosentraeger";
            case CUSTOM_PERM_SCISSORS:
                return "custom_perm_scissors";
            default:
                throw new Exception(I18n.f("layout.unknownComponent", type.toString()));
                //return "unknown";
        }
    }
    
    /**
     * Gets the number of possible orientations for this tile type
     * @return 
     */
    public int getNumOrientations()
    {        
        // y axis symmetry - limit rotation options
        if (this.type == componentType.STRAIGHT || this.type == componentType.FEEDBACK 
                || this.type == componentType.ROUTE || this.type == componentType.SWITCH_CROSSING
                || this.type == componentType.OVERPASS)
        {
            return 2;
        }
        // x and y symmetry - rotation not needed
        else if (this.type == componentType.TURNTABLE || this.type == componentType.CROSSING)
        {
            return 1;
        }
        else
        {
            return 4;
        }
    }
    
    /**
     * Rotates the component
     */
    public void rotate()
    {
        this.orientation = (this.orientation + 1) % getNumOrientations();
    }
    
    /**
     * Updates the component's address
     * @param address 
     * @param protocol 
     * @param isGreen used for uncouplers
     * @throws java.lang.Exception 
     */
    public void setLogicalAddress(int address, Accessory.accessoryDecoderType protocol, boolean isGreen) throws Exception
    {               
        // Logical address is 2x the raw address
        if (!this.isFeedback() && !this.isLink() && !this.isRoute())
        {
            // 3-way switches have an address 1 above the base, so make the check more strict
            if (!Accessory.isValidAddress(this.isThreeWay() ? address : address - 1, protocol))
            {
                throw new Exception(I18n.t("acc.invalidAddress"));
            }
            
            this.rawAddress = address * 2;

            // The logical address, which is what the file parser derives (rawAddress / 2, floored) and
            // what every consumer of getAddress() assumes - syncLayouts passes it as the logical address
            // and getAddress() - 1 as the raw one.  This used to be set to address * 2 as well, leaving
            // the two fields equal and getAddress() returning double the truth.
            this.address = address;

            if (this.isUncoupler() && isGreen)
            {
                // Only the raw address distinguishes green from red - isLogicalGreen() reads its low bit.
                // The parser floors 2N+1 back to N, so the logical address is the same either way.
                this.rawAddress += 1;
            }
        }
        else
        {
            // User doesn't need to know that pages are 0-based
            if (this.isLink())
            {
                address -= 1;
            }
            
            if (address < 0)
            {
                throw new Exception(I18n.t("acc.invalidAddressPositive"));
            }
            
            this.address = address;
            this.rawAddress = address;
        }
    }
    
    /**
     * Odd addresses are controlled by the green button - used for uncouplers
     * @return 
     */
    /**
     * Whether this component's orientation is still the one the file's rotation produced.
     *
     * The parser turns some signal types by a step on the way in, so the stored orientation and the
     * file's number legitimately differ; what matters is whether the user has changed it since.
     */
    private boolean orientationMatchesFile()
    {
        if (originalDrehung == null) return false;

        if (originalTyp != null && originalTyp.contains("_f_"))
        {
            return this.orientation == Math.floorMod(originalDrehung - 1, 4);
        }

        return this.orientation == originalDrehung.intValue();
    }

    public boolean isLogicalGreen()
    {
        return rawAddress % 2 != 0;
    }
    
    /**
     * Gets the human readable version of the address
     * @return 
     */
    public int getLogicalAddress()
    {
        if (this.isFeedback() || this.isRoute())
        {
            return this.rawAddress;
        }
        else if (this.isLink())
        {
            return this.rawAddress + 1;
        }
        else
        {
            if (rawAddress % 2 == 0)
            {
                return (rawAddress / 2);
            }
            else
            {
                return (rawAddress - 1) / 2;
            }
        }
    }
    
    public accessoryDecoderType getProtocol()
    {
        return protocol;
    }

    public void setProtocol(accessoryDecoderType protocol)
    {
        this.protocol = protocol;
    }
}
