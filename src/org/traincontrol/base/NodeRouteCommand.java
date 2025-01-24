package org.traincontrol.base;

import java.util.Objects;
import org.json.JSONObject;
import org.traincontrol.model.ViewListener;

/**
 * This class represents a boolean expression for route conditions
 */
public class NodeRouteCommand extends NodeExpression
{
    private static final long serialVersionUID = 1L;

    private final RouteCommand command;

    public NodeRouteCommand(RouteCommand command)
    {
        this.command = command;
    }
    
    public RouteCommand getRouteCommand()
    {
        return command;
    }

    @Override
    public boolean evaluate(ViewListener network)
    {
        return Route.evaluate(command, network);
    }
    
    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (obj == null || getClass() != obj.getClass())
        {
            return false;
        }
        
        NodeRouteCommand that = (NodeRouteCommand) obj;
        return Objects.equals(command, that.command);
    }

    @Override
    public int hashCode()
    {
        int hash = 7;
        hash = 47 * hash + Objects.hashCode(this.command);
        return hash;
    }
    
    @Override
    public JSONObject toJSON() throws Exception
    {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("type", "NodeRouteCommand");
        jsonObject.put("command", command.toJSON());
        return jsonObject;
    }

    public static NodeRouteCommand fromJSON(JSONObject jsonObject)
    {
        RouteCommand command = RouteCommand.fromJSON(jsonObject.getJSONObject("command"));
        return new NodeRouteCommand(command);
    }
}

