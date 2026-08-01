package org.traincontrol.base;

import java.util.Objects;
import org.json.JSONObject;
import org.traincontrol.model.ViewListener;

/**
 * This class represents a boolean expression for route conditions
 */
public class NodeOr extends NodeExpression
{
    private static final long serialVersionUID = 1L;

    private final NodeExpression left;
    private final NodeExpression right;

    public NodeOr(NodeExpression left, NodeExpression right)
    {
        this.left = left;
        this.right = right;
    }

    public NodeExpression getLeft()
    {
        return left;
    }
    
    public NodeExpression getRight()
    {
        return right;
    }
    
    @Override
    public boolean evaluate(ViewListener network)
    {
        return left.evaluate(network) || right.evaluate(network);
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
        NodeOr that = (NodeOr) obj;
        return Objects.equals(left, that.left) && Objects.equals(right, that.right);
    }

    @Override
    public int hashCode()
    {
        int hash = 5;
        hash = 17 * hash + Objects.hashCode(this.left);
        hash = 17 * hash + Objects.hashCode(this.right);
        return hash;
    }
    
    @Override
    public JSONObject toJSON() throws Exception
    {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("type", "NodeOr");
        jsonObject.put("left", left.toJSON());
        jsonObject.put("right", right.toJSON());
        return jsonObject;
    }

    public static NodeOr fromJSON(JSONObject jsonObject)
    {
        NodeExpression left = NodeExpression.fromJSON(jsonObject.getJSONObject("left"));
        NodeExpression right = NodeExpression.fromJSON(jsonObject.getJSONObject("right"));

        // A bare NodeAnd as the LEFT child is a shape the text parser can never produce - it applies
        // stacked operators LIFO, so text-origin trees are right-nested and their left children are
        // always leaves or groups.  Hand-written JSON can build it, and rendering it bare changed its
        // meaning on the next editor round trip: "a AND b OR c" reparses right-nested.  Wrapping it
        // in a group here makes the serializer emit the parentheses that preserve it, and touches no
        // tree that text could have built - which is what keeps the editor round trip byte-identical
        // for everything users authored as text.
        if (left instanceof NodeAnd)
        {
            left = new NodeGroup(java.util.Arrays.asList(left));
        }
        return new NodeOr(left, right);
    }

}
