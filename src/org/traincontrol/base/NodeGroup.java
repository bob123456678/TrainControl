package org.traincontrol.base;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.json.JSONArray;
import org.json.JSONObject;
import org.traincontrol.model.ViewListener;

/**
 * This class represents a grouping of boolean expressions for route conditions.
 */
public class NodeGroup extends NodeExpression
{
    private static final long serialVersionUID = 1L;
    
    private final List<NodeExpression> expressions;

    public NodeGroup(List<NodeExpression> expressions)
    {
        this.expressions = expressions;
    }
    
    public List<NodeExpression> getExpressions()
    {
        return expressions;
    }

    @Override
    public boolean evaluate(ViewListener network)
    {
        for (NodeExpression expression : expressions)
        {
            if (!expression.evaluate(network))
            {
                return false;
            }
        }
        return true;
    }
    
    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) return true;
        
        if (obj == null || getClass() != obj.getClass())
        {
            return false;
        }
        
        NodeGroup that = (NodeGroup) obj;
        return Objects.equals(expressions, that.expressions);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(expressions);
    }
    
    @Override
    public JSONObject toJSON() throws Exception
    {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("type", "NodeGroup");
        JSONArray jsonArray = new JSONArray();
        for (NodeExpression expression : expressions)
        {
            jsonArray.put(expression.toJSON());
        }
        jsonObject.put("expressions", jsonArray);
        return jsonObject;
    }

    public static NodeGroup fromJSON(JSONObject jsonObject)
    {
        JSONArray jsonArray = jsonObject.getJSONArray("expressions");
        List<NodeExpression> expressions = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++)
        {
            expressions.add(NodeExpression.fromJSON(jsonArray.getJSONObject(i)));
        }
        return new NodeGroup(expressions);
    }
}
