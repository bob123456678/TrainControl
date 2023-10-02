package marklin;

import base.RouteCommand;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
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
    private Integer departureFunction;
    private Integer arrivalFunction;
    private boolean reversible;
    private Integer trainLength;
    private long totalRuntime;
    private long historicalOperatingTime;
    
    // Route state
    private int s88;
    private MarklinRoute.s88Triggers s88TriggerType;
    private boolean routeEnabled;
    private Map<Integer, Boolean> conditionS88s; // If we use a different data structure, this can be changed to Object to avoid unserialization issues 
    
    // Route state
    private Object route; // If we use a different data structure, this can be changed to Object to avoid unserialization issues 

    // Track class version to avoid resetting state every time
    private static final long serialVersionUID = -9111893030704758839L;
    
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
        this.conditionS88s = r.getConditionS88s();
    }
    
    /**
     * Returns the locomotive decoderType, or null if not a locomotive
     * @return 
     */
    public MarklinLocomotive.decoderType getLocType()
    {
        if (null != this.type)
        {
            switch (this.type)
            {
                case LOC_MFX:
                    return MarklinLocomotive.decoderType.MFX;
                case LOC_DCC:
                    return MarklinLocomotive.decoderType.DCC;
                case LOC_MULTI_UNIT:
                    return MarklinLocomotive.decoderType.MULTI_UNIT;
                case LOC_MM2:
                    return MarklinLocomotive.decoderType.MM2;
                default:
                    break;
            }
        }
        
        return null;
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
        this.departureFunction = l.getDepartureFunc();
        this.arrivalFunction = l.getArrivalFunc();
        this.reversible = l.isReversible();
        this.trainLength = l.getTrainLength();
        this.totalRuntime = l.getTotalRuntime();
        this.historicalOperatingTime = l.getHistoricalOperatingTime();
    }
    
    public List<RouteCommand> getRoute()
    {
        if (this.route instanceof LinkedHashMap)
        {
            List<RouteCommand> rcs = new LinkedList<>();
            
            LinkedHashMap<Integer, Boolean> tempRoute = (LinkedHashMap<Integer, Boolean>) this.route;
            for (Integer key : tempRoute.keySet() )
            {
                rcs.add(RouteCommand.RouteCommandAccessory(key, tempRoute.get(key)));
            }
            
            this.route = rcs;

        }
        // Handle conversion from old map
        else if (this.route instanceof HashMap)
        {
            List<RouteCommand> rcs = new LinkedList<>();
            
            HashMap<Integer, Boolean> tempRoute = (HashMap<Integer, Boolean>) this.route;
            for (Integer key : tempRoute.keySet() )
            {
                rcs.add(RouteCommand.RouteCommandAccessory(key, tempRoute.get(key)));
            }
            
            this.route = rcs;
        }

        return (List<RouteCommand>) this.route;
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
    
    public Map<Integer, Boolean> getConditionS88s()
    {
        Map<Integer, Boolean> conditions = new HashMap<>();
        
        try
        {
            conditions = (Map<Integer, Boolean>) this.conditionS88s;
        }
        catch (Exception e)
        {
            System.out.println("Route " + this.getName() + " conditions have been reset.");
        }

        return conditions;
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
    
    public Integer getDepartureFunction()
    {
        return this.departureFunction;
    }
    
    public Integer getArrivalFunction()
    {
        return this.arrivalFunction;
    }
    
    public boolean getReversible()
    {
        return this.reversible;
    }
    
    public long getTotalRuntime()
    {
        return this.totalRuntime;
    }
    
    public long getHistoricalOperatingTime()
    {
        return this.historicalOperatingTime;
    }
    
    public Integer getTrainLength()
    {
        if (this.trainLength instanceof Integer)
        {
            return this.trainLength;
        }
        else
        {
            return 0;
        }
    }
}
