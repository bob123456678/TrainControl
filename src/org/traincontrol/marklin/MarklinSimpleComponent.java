package org.traincontrol.marklin;

import java.util.ArrayList;
import org.traincontrol.base.RouteCommand;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.traincontrol.base.NodeExpression;
import org.traincontrol.marklin.MarklinAccessory.accessoryDecoderType;

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
    private int numActuations = 0;
    private accessoryDecoderType accessoryDecoderType = null;
    
    // Locomotive function state and types
    private boolean[] functions;
    private boolean[] preferredFunctions;
    private int[] functionTypes;
    private int[] functionTriggerTypes;
    private int preferredSpeed;
    private Integer departureFunction;
    private Integer arrivalFunction;
    private boolean reversible;
    private Integer trainLength;
    private Map<String, Long> historicalOperatingTimeNew;
    private String localImageURL;
    private boolean customFunctions;
    private Map<Integer, String> localFunctionImageURLs;
    private String notes;
    private Map<String, Double> linkedLocomotives;
    private Map<String, Double> centralStationLinkedLocomotives;
 
    // Route state
    private int s88;
    private MarklinRoute.s88Triggers s88TriggerType;
    private boolean routeEnabled; 
    private NodeExpression conditions;
    
    // Legacy
    private Map<Integer, Boolean> conditionS88s; // If we use a different data structure, this can be changed to Object to avoid unserialization issues 
    private List<RouteCommand> conditionAccessoroes;
    // End legacy
    
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
        this.numActuations = a.getNumActuations();
        this.accessoryDecoderType = a.getDecoderType();
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
        this.conditions = r.getConditions();
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
        this.functionTriggerTypes = l.getFunctionTriggerTypes();
        this.preferredFunctions = l.getPreferredFunctions();
        this.preferredSpeed = l.getPreferredSpeed();
        this.departureFunction = l.getDepartureFunc();
        this.arrivalFunction = l.getArrivalFunc();
        this.reversible = l.isReversible();
        this.trainLength = l.getTrainLength();
        // this.totalRuntime = l.getTotalRuntime(); // deprecated
        this.historicalOperatingTimeNew = l.getHistoricalOperatingTime();
        this.localImageURL = l.getLocalImageURL();
        this.customFunctions = l.isCustomFunctions();
        this.localFunctionImageURLs = l.getLocalFunctionImageURLs();
        this.notes = l.getNotes();
        this.linkedLocomotives = l.getLinkedLocomotiveNames();
        this.centralStationLinkedLocomotives = l.getCentralStationMultiUnitLocomotiveNames();
    }
    
    public Map<String, Double> getCentralStationLinkedLocomotives()
    {
        return this.centralStationLinkedLocomotives;
    }
    
    public Map<String, Double> getLinkedLocomotives()
    {
        return this.linkedLocomotives;
    }
    
    public List<RouteCommand> getRoute()
    {
        if (this.route instanceof LinkedHashMap)
        {
            List<RouteCommand> rcs = new LinkedList<>();
            
            LinkedHashMap<Integer, Boolean> tempRoute = (LinkedHashMap<Integer, Boolean>) this.route;
            for (Integer key : tempRoute.keySet())
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
    
    public int getNumActuations()
    {
        return numActuations;
    }
    
    public boolean[] getFunctions()
    {
        return functions;
    }
    
    public int[] getFunctionTypes()
    {
        return functionTypes;
    }
    
    public int[] getFunctionTriggerTypes()
    {
        if (functionTriggerTypes == null) return new int[0];
        
        return functionTriggerTypes;
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
    
    public NodeExpression getConditions()
    {
        if (this.conditions != null && this.conditions instanceof NodeExpression)
        {
            return (NodeExpression) this.conditions;
        }
        
        // Legacy
        List<RouteCommand> output = new ArrayList<>();
        
        if (this.conditions != null && this.conditions instanceof List)
        {
            output.addAll((List<RouteCommand>) this.conditions);
        }
        
        if (this.conditionAccessoroes != null)
        {
            output.addAll(this.conditionAccessoroes);
        }
        
        if (this.conditionS88s != null)
        {
            for (Entry<Integer, Boolean> e: this.conditionS88s.entrySet())
            {
                output.add(RouteCommand.RouteCommandFeedback(e.getKey(), e.getValue()));
            }
        }
        
        return NodeExpression.fromList(output);
        // End legacy
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
    
    public Map<String, Long> getHistoricalOperatingTime()
    {
        if (this.historicalOperatingTimeNew == null) return new HashMap<>();
        
        return (Map<String, Long>) this.historicalOperatingTimeNew;   
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
    
    public String getLocNotes()
    {
        return this.notes;
    }
    
    public String getLocalImageURL()
    {
        return this.localImageURL;
    }
    
    public boolean getCustomFunctions()
    {
        return this.customFunctions;
    }

    public Map<Integer, String> getLocalFunctionImageURLs()
    {
        return localFunctionImageURLs;
    }
    
    public accessoryDecoderType getAccessoryDecoderType()
    {
        return accessoryDecoderType;
    }
}
