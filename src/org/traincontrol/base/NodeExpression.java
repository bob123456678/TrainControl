package org.traincontrol.base;

import org.traincontrol.model.ViewListener;
import java.io.Serializable; 
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;
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
    
    public static String toTextRepresentation(NodeExpression expression, ViewListener network)
    {
        StringBuilder sb = new StringBuilder();
        toTextRepresentationHelper(expression, sb, network);
        return sb.toString().replaceAll("\n+", "\n").trim(); // Remove empty lines and trailing newline
    }

    // TODO this does not work
    private static void toTextRepresentationHelper(NodeExpression node, StringBuilder sb, ViewListener network)
    {
        if (node instanceof NodeRouteCommand)
        {
            RouteCommand command = ((NodeRouteCommand) node).getRouteCommand();
            sb.append(command.toLine(network.getAccessoryByAddress(command.getAddress()))).append("\n");
        }
        else if (node instanceof NodeAnd)
        {
            sb.append("(");
            toTextRepresentationHelper(((NodeAnd) node).getLeft(), sb, network);
            toTextRepresentationHelper(((NodeAnd) node).getRight(), sb, network);
            sb.append(")");
        }
        else if (node instanceof NodeOr)
        {
            toTextRepresentationHelper(((NodeOr) node).getLeft(), sb, network);
            sb.append("OR\n");
            toTextRepresentationHelper(((NodeOr) node).getRight(), sb, network);
        }
    }
    
     public static NodeExpression fromTextRepresentation(String text, ViewListener network) throws Exception
    {
        List<String> lines = preprocessText(text);
        Stack<NodeExpression> stack = new Stack<>();
        Stack<String> operators = new Stack<>();

        for (String line : lines)
        {
            line = line.trim();
            if (line.equals("OR"))
            {
                if (stack.isEmpty())
                {
                    throw new Exception("Invalid expression: 'OR' cannot be at the beginning or end of the expression.");
                }
                operators.push("OR");
            }
            else if (line.equals("("))
            {
                operators.push("(");
            }
            else if (line.equals(")"))
            {
                while (!operators.isEmpty() && !operators.peek().equals("("))
                {
                    if (operators.pop().equals("OR"))
                    {
                        NodeExpression right = stack.pop();
                        NodeExpression left = stack.pop();
                        stack.push(new NodeOr(left, right));
                    }
                }
                if (!operators.isEmpty() && operators.peek().equals("("))
                {
                    operators.pop();
                }
            }
            else
            {
                if (line.trim().length() > 0)
                {
                    stack.push(parseLine(line, network));
                }
            }
        }

        while (!operators.isEmpty() && !operators.peek().equals("("))
        {
            if (operators.pop().equals("OR"))
            {
                NodeExpression right = stack.pop();
                NodeExpression left = stack.pop();
                stack.push(new NodeOr(left, right));
            }
        }

        if (!operators.isEmpty() || stack.size() > 1)
        {
            throw new Exception("Mismatched parentheses or incomplete expression.");
        }

        return stack.pop();
    }

    private static List<String> preprocessText(String text)
    {
        text = text.replaceAll("\\(", "\n(\n").replaceAll("\\)", "\n)\n").replaceAll("OR", "\nOR\n");
        return Arrays.asList(text.split("\n"));
    }

    private static NodeExpression parseLine(String line, ViewListener network) throws Exception
    {
        RouteCommand rc = RouteCommand.fromLine(line);
        if (!rc.isAccessory() && !rc.isFeedback())
        {
            throw new Exception("Accessory Conditions must be accessory or feedback commands.");
        }
        return new NodeRouteCommand(rc);
    }
}
