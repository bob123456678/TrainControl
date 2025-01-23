package org.traincontrol.base;

import org.traincontrol.model.ViewListener;

public class NodeAnd extends NodeExpression
{
    private final NodeExpression left;
    private final NodeExpression right;

    public NodeAnd(NodeExpression left, NodeExpression right)
    {
        this.left = left;
        this.right = right;
    }

    @Override
    boolean evaluate(ViewListener network)
    {
        return left.evaluate(network) && right.evaluate(network);
    }
}
