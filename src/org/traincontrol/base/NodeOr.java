package org.traincontrol.base;

import org.traincontrol.model.ViewListener;

public class NodeOr extends NodeExpression
{
    private final NodeExpression left;
    private final NodeExpression right;

    public NodeOr(NodeExpression left, NodeExpression right)
    {
        this.left = left;
        this.right = right;
    }

    @Override
    boolean evaluate(ViewListener network)
    {
        return left.evaluate(network) || right.evaluate(network);
    }
}
