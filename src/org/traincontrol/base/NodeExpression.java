package org.traincontrol.base;

import org.traincontrol.model.ViewListener;
import java.io.Serializable; 
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;
import org.json.JSONObject;
import org.traincontrol.util.I18n;

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
        else if (node instanceof NodeGroup)
        {
            for (NodeExpression expr : ((NodeGroup) node).getExpressions())
            {
                collectCommandsHelper(expr, commands);
            }
        }
    }
    
    public abstract JSONObject toJSON() throws Exception;

    /**
     * Wraps every bare cross-operator LEFT child in a group, recursively.
     *
     * The text parser applies stacked operators LIFO, so text-origin trees are right-nested and
     * their left children are always leaves or groups - this is a no-op for everything users author
     * as text, which is what keeps the editor round trip byte-identical (testExpressions pins it).
     * Two doors can build the unreachable shape: hand-written structural JSON, and the locomotive
     * database, which Java-serializes condition trees and restores them without a parse.  Rendered
     * bare, such a tree changes meaning on the next editor round trip ("a AND b OR c" reparses
     * right-nested); grouped, the serializer emits the parentheses that preserve it.  Idempotent,
     * and null-safe because routes without conditions carry null.
     */
    public static NodeExpression normalize(NodeExpression expr)
    {
        if (expr instanceof NodeAnd)
        {
            NodeExpression left = normalize(((NodeAnd) expr).getLeft());
            NodeExpression right = normalize(((NodeAnd) expr).getRight());

            if (left instanceof NodeOr)
            {
                left = new NodeGroup(java.util.Arrays.asList(left));
            }

            return new NodeAnd(left, right);
        }
        else if (expr instanceof NodeOr)
        {
            NodeExpression left = normalize(((NodeOr) expr).getLeft());
            NodeExpression right = normalize(((NodeOr) expr).getRight());

            if (left instanceof NodeAnd)
            {
                left = new NodeGroup(java.util.Arrays.asList(left));
            }

            return new NodeOr(left, right);
        }
        else if (expr instanceof NodeGroup)
        {
            List<NodeExpression> normalized = new ArrayList<>();

            for (NodeExpression child : ((NodeGroup) expr).getExpressions())
            {
                normalized.add(normalize(child));
            }

            return new NodeGroup(normalized);
        }

        return expr;
    }

    public static NodeExpression fromJSON(JSONObject jsonObject)
    {
        String type = jsonObject.getString("type");
        switch (type)
        {
            case "NodeRouteCommand":
                return NodeRouteCommand.fromJSON(jsonObject);
            case "NodeAnd":
                // normalize: see its javadoc - hand-written JSON is one of the two doors that can
                // build shapes the text parser cannot
                return normalize(NodeAnd.fromJSON(jsonObject));
            case "NodeGroup":
                return normalize(NodeGroup.fromJSON(jsonObject));
            case "NodeOr":
                return normalize(NodeOr.fromJSON(jsonObject));
            default:
                throw new IllegalArgumentException(
                    I18n.f("error.unknownNodeExpressionType", type)
                );
        }
    }
    
    /**
     * Converts this expression to a parseable text representation
     * @param expression
     * @param network
     * @return 
     */
    public static String toTextRepresentation(NodeExpression expression, ViewListener network)
    {
        StringBuilder sb = new StringBuilder();
        toTextRepresentationHelper(expression, sb, network);
        return sb.toString().replaceAll("\n+", "\n").replaceAll("\n[ ]+AND", "\nAND").replaceAll("\n[ ]+OR", "\nOR").replaceAll("\n\\)", ")").trim(); // Remove empty lines and trailing newline
    }

    private static void toTextRepresentationHelper(NodeExpression node, StringBuilder sb, ViewListener network)
    {
        if (node instanceof NodeRouteCommand)
        {
            RouteCommand command = ((NodeRouteCommand) node).getRouteCommand();
            
            // We need to make this check to prevent invalid lookups of S88 addresses
            Accessory acc = null;

            if (command.isAccessory())
            {
                // Resolved by name, which carries the command's own protocol.  This used to pass
                // getAccessoryType() - "Switch" or "Signal" - into determineAccessoryDecoderType, which
                // could not parse it and silently fell back to MM2, so a DCC accessory was looked up
                // under the wrong protocol.  Going through getAccessoryByName also means this display
                // path no longer invents an accessory when the address is unused, the way
                // getAccessoryByAddress does.  Either type resolves, since they are one decoder.
                acc = network.getAccessoryByName(
                    Accessory.accessoryTypeToPrettyString(Accessory.accessoryType.SWITCH) + " "
                    + command.getAddress()
                    + Accessory.getProtocolStringForName(command.getProtocol().toString()));
            }
            
            sb.append(command.toLine(acc)).append("\n");
        }
        else if (node instanceof NodeAnd)
        {
            toTextRepresentationHelper(((NodeAnd) node).getLeft(), sb, network);
            sb.append("\nAND\n");
            toTextRepresentationHelper(((NodeAnd) node).getRight(), sb, network);
        }
        else if (node instanceof NodeOr)
        {
            toTextRepresentationHelper(((NodeOr) node).getLeft(), sb, network);
            sb.append("\nOR\n");
            toTextRepresentationHelper(((NodeOr) node).getRight(), sb, network);
        }
        else if (node instanceof NodeGroup)
        {
            sb.append("(");
            List<NodeExpression> expressions = ((NodeGroup) node).getExpressions();
            for (int i = 0; i < expressions.size(); i++)
            {
                toTextRepresentationHelper(expressions.get(i), sb, network);
                if (i == expressions.size() - 1)
                {
                    sb.append(")");
                }
            }
        }
    }

    /**
     * Converts a text representation into a complete expression
     * @param text
     * @param network
     * @return
     * @throws Exception 
     */
    public static NodeExpression fromTextRepresentation(String text, ViewListener network) throws Exception 
    {
        List<String> lines = preprocessText(text);
        Stack<NodeExpression> stack = new Stack<>();
        Stack<String> operators = new Stack<>();

        for (int i = 0; i < lines.size(); i++)
        {
            String line = lines.get(i).trim();

            if (line.equals("OR")) 
            {
                operators.push("OR");
            } 
            else if (line.equals("AND")) 
            {
                operators.push("AND");
            } 
            else if (line.equals("(")) 
            {
                operators.push("(");
            } 
            else if (line.equals(")")) 
            {
                while (!operators.isEmpty() && !operators.peek().equals("(")) 
                {
                    String op = operators.pop();
                    NodeExpression right = stack.pop();
                    NodeExpression left = stack.pop();
                    if (op.equals("AND")) 
                    {
                        stack.push(new NodeAnd(left, right));
                    } 
                    else if (op.equals("OR")) 
                    {
                        stack.push(new NodeOr(left, right));
                    }
                }
                operators.pop(); // Remove the '('
                NodeExpression group = stack.pop();
                stack.push(new NodeGroup(Arrays.asList(group)));
            } 
            else 
            {
                if (!line.isEmpty()) 
                {
                    stack.push(parseLine(line));
                }
            }
        }

        while (!operators.isEmpty()) 
        {
            String op = operators.pop();
            NodeExpression right = stack.pop();
            NodeExpression left = stack.pop();
            if (op.equals("AND")) 
            {
                stack.push(new NodeAnd(left, right));
            } 
            else if (op.equals("OR")) 
            {
                stack.push(new NodeOr(left, right));
            }
        }

        if (stack.size() != 1) 
        {
            throw new Exception(
                I18n.f("error.invalidExpressionMismatchedOperatorsOrParentheses")
            );
        }

        return stack.pop();
    }

    private static List<String> preprocessText(String text)
    {
        // \b so that AND and OR are only split out when they stand alone.  Without it these were plain
        // substring replacements, so a locomotive name in an autoloc condition containing them - NORD,
        // MOTOR, ORIENT, GRAND - was cut in half and the expression failed to parse.
        text = text.replaceAll("\\(", "\n(\n").replaceAll("\\)", "\n)\n")
                   .replaceAll("\\bAND\\b", "\nAND\n").replaceAll("\\bOR\\b", "\nOR\n");
        List<String> lines = Arrays.asList(text.split("\n"));
        List<String> filteredLines = new ArrayList<>();

        for (String line : lines)
        {
            if (!line.trim().isEmpty())
            {
                filteredLines.add(line);
            }
        }

        return filteredLines;
    }

    private static NodeExpression parseLine(String line) throws Exception
    {
        RouteCommand rc = RouteCommand.fromLine(line, false);
        
        if (!rc.isConditionCommand())
        {
            throw new Exception(
                I18n.f("error.invalidCondition")
            );
        }
        
        return new NodeRouteCommand(rc);
    }
}
