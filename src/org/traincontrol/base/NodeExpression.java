package org.traincontrol.base;

import org.traincontrol.model.ViewListener;

public abstract class NodeExpression
{
    abstract boolean evaluate(ViewListener network);
}
