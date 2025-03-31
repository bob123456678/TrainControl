package org.traincontrol.base;

import org.traincontrol.model.ViewListener;
import java.io.Serializable; 
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;
import org.json.JSONObject;
import org.traincontrol.marklin.MarklinAccessory;

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

    public static NodeExpression fromJSON(JSONObject jsonObject)
    {
        String type = jsonObject.getString("type");
        switch (type)
        {
            case "NodeRouteCommand":
                return NodeRouteCommand.fromJSON(jsonObject);
            case "NodeAnd":
                return NodeAnd.fromJSON(jsonObject);
            case "NodeGroup":
                return NodeGroup.fromJSON(jsonObject);
            case "NodeOr":
                return NodeOr.fromJSON(jsonObject);
            default:
                throw new IllegalArgumentException("Unknown NodeExpression type: " + type);
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
        return sb.toString().replaceAll("\n+", "\n").replaceAll("\n[ ]+OR", "\nOR").replaceAll("\n\\)", ")").trim(); // Remove empty lines and trailing newline
    }

    private static void toTextRepresentationHelper(NodeExpression node, StringBuilder sb, ViewListener network)
    {
        if (node instanceof NodeRouteCommand)
        {
            RouteCommand command = ((NodeRouteCommand) node).getRouteCommand();
            
            // We need to make this check to prevent invalid lookups of S88 addresses
            // TODO - the RouteCommand should maintain the decoder type
            Accessory acc = null;
            if (command.isAccessory()) acc = network.getAccessoryByAddress(command.getAddress(),
                MarklinAccessory.determineDecoderType(command.getAddress() - 1)
            );
            
            sb.append(command.toLine(acc)).append("\n");
        }
        else if (node instanceof NodeAnd)
        {
            toTextRepresentationHelper(((NodeAnd) node).getLeft(), sb, network);
            sb.append("\n");
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
                while (!operators.isEmpty() && operators.peek().equals("AND")) 
                {
                    operators.pop();
                    NodeExpression right = stack.pop();
                    NodeExpression left = stack.pop();
                    stack.push(new NodeAnd(left, right));
                }
                operators.push("OR");
            } 
            else if (line.equals("(")) 
            {
                // Handle implicit AND before a group
                if (i > 0 && !lines.get(i - 1).trim().equals("OR") && !lines.get(i - 1).trim().equals("(") && !lines.get(i - 1).trim().equals(")"))
                {
                    operators.push("AND");
                }
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

                // Handle implicit AND after a group
                if (i + 1 < lines.size())
                {
                    String nextLine = lines.get(i + 1).trim();
                    if (!nextLine.equals("OR") && !nextLine.equals(")"))
                    {
                        operators.push("AND");
                    }
                }
            } 
            else 
            {
                if (!line.isEmpty()) 
                {
                    stack.push(parseLine(line));

                    if (i + 1 < lines.size())
                    {
                        String nextLine = lines.get(i + 1).trim();
                        if (!nextLine.equals("OR") && !nextLine.equals("(") && !nextLine.equals(")"))
                        {
                            operators.push("AND");
                        }
                    }
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
            throw new Exception("Invalid expression: mismatched operators or parentheses.");
        }

        return stack.pop();
    }

    private static List<String> preprocessText(String text)
    {
        text = text.replaceAll("\\(", "\n(\n").replaceAll("\\)", "\n)\n").replaceAll("OR", "\nOR\n");
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
        RouteCommand rc = RouteCommand.fromLine(line);
        
        if (!rc.isAccessory() && !rc.isFeedback())
        {
            throw new Exception("Conditions can only contain accessory or feedback settings.");
        }
        
        return new NodeRouteCommand(rc);
    }
}
