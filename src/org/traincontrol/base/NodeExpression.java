package org.traincontrol.base;

import org.traincontrol.model.ViewListener;
import java.io.Serializable; 
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

/**
 * This class represents a boolean expression for route conditions
 */
public abstract class NodeExpression implements Serializable 
{
    private static final long serialVersionUID = 1L;
    
    abstract public boolean evaluate(ViewListener network);
    
    public static NodeExpression fromList(List<RouteCommand> commands)
    {
        if (commands == null || commands.isEmpty())
        {
            return null;
        }

        // Start with the first command
        NodeExpression expression = new NodeRouteCommand(commands.get(0));

        // Combine with AND operation if there are more commands
        for (int i = 1; i < commands.size(); i++)
        {
            expression = new NodeAnd(expression, new NodeRouteCommand(commands.get(i)));
        }

        return expression;
    }
    
    public static List<RouteCommand> toList(NodeExpression expression)
    {
        List<RouteCommand> commands = new ArrayList<>();
        collectCommandsHelper(expression, commands);
        return commands;
    }

    private static void collectCommandsHelper(NodeExpression node, List<RouteCommand> commands)
    {
        if (node instanceof NodeRouteCommand)
        {
            commands.add(((NodeRouteCommand) node).getRouteCommand());
        }
        else if (node instanceof NodeAnd)
        {
            collectCommandsHelper(((NodeAnd) node).getLeft(), commands);
            collectCommandsHelper(((NodeAnd) node).getRight(), commands);
        }
        else if (node instanceof NodeOr)
        {
            collectCommandsHelper(((NodeOr) node).getLeft(), commands);
            collectCommandsHelper(((NodeOr) node).getRight(), commands);
        }
    }
    
    public abstract JSONObject toJSON() throws Exception;

    public static NodeExpression fromJSON(JSONObject jsonObject)
    {
        String type = jsonObject.getString("type");
        switch (type)
        {
            case "NodeRouteCommand":
                return NodeRouteCommand.fromJSON(jsonObject);
            case "NodeAnd":
                return NodeAnd.fromJSON(jsonObject);
            case "NodeOr":
                return NodeOr.fromJSON(jsonObject);
            default:
                throw new IllegalArgumentException("Unknown NodeExpression type: " + type);
        }
    }
}
