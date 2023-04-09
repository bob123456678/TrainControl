package marklin;

import java.util.Map;

/**
 * Serializable class for saving state
 * @author Adam
 */
public class MarklinSimpleComponent implements java.io.Serializable
{
    public enum Type {LOC_MFX, LOC_MM2, LOC_DCC, LOC_MULTI_UNIT, SWITCH, SIGNAL, ROUTE, FEEDBACK};
    
    private final String name;
    private final int address;
    private final Type type;
    
    // Switch state or loc directions
    private boolean state;
    
    // Locomotive function state and types
    private boolean[] functions;
    private boolean[] preferredFunctions;
    private int[] functionTypes;
    private int preferredSpeed;
    
    // Route state
    private int s88;
    private MarklinRoute.s88Triggers s88TriggerType;
    private boolean routeEnabled;
    
    // Route state
    private Map<Integer, Boolean> route;
    
    public MarklinSimpleComponent(MarklinAccessory a)
    {
        if (a.isSignal())
        {
            this.type = Type.SIGNAL;
        }
        else
        {
            this.type = Type.SWITCH;
        }
        
        this.name = a.getName();
        this.address = a.getAddress();
        this.state = a.isSwitched();
    }
    
    public MarklinSimpleComponent(MarklinFeedback a)
    {
        this.type = Type.FEEDBACK;
        
        this.name = a.getName();
        this.address = a.getUID();
        this.state = a.isSet();
    }
    
    public MarklinSimpleComponent(MarklinRoute r)
    {
        this.name = r.getName();
        this.type = Type.ROUTE;
        
        this.route = r.getRoute();
        this.address = r.getId();
        this.s88 = r.getS88();
        this.s88TriggerType = r.getTriggerType();
        this.routeEnabled = r.isEnabled();
    }
    
    public MarklinSimpleComponent(MarklinLocomotive l)
    {
        if (l.getDecoderType() == MarklinLocomotive.decoderType.MFX)
        {
            this.type = Type.LOC_MFX;
        }
        else if (l.getDecoderType() == MarklinLocomotive.decoderType.DCC)
        {
            this.type = Type.LOC_DCC;
        }
        else if (l.getDecoderType() == MarklinLocomotive.decoderType.MULTI_UNIT)
        {
            this.type = Type.LOC_MULTI_UNIT;
        }
        else
        {
            this.type = Type.LOC_MM2;
        }
        
        this.name = l.getName();
        this.address = l.getAddress();
        this.state = l.goingForward();
        this.functions = l.getFunctionState();
        this.functionTypes = l.getFunctionTypes();
        this.preferredFunctions = l.getPreferredFunctions();
        this.preferredSpeed = l.getPreferredSpeed();
    }
    
    public Map<Integer, Boolean> getRoute()
    {
        return this.route;
    }
    
    public boolean[] getFunctions()
    {
        return functions;
    }
    
    public int[] getFunctionTypes()
    {
        return functionTypes;
    }
    
    public boolean getState()
    {
        return state;
    }

    public String getName()
    {
        return name;
    }

    public int getS88()
    {
        return s88;
    }
    
    public MarklinRoute.s88Triggers getS88TriggerType()
    {
        return s88TriggerType;
    }
    
    public boolean getRouteEnabled()
    {
        return routeEnabled;
    }
    
    public int getAddress()
    {
        return address;
    }

    public Type getType()
    {
        return type;
    }
    
    public boolean[] getPreferredFunctions()
    {
        return preferredFunctions;
    }    
    
    public int getPreferredSpeed()
    {
        return preferredSpeed;
    }    
}
