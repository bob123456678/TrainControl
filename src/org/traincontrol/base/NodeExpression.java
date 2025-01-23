package org.traincontrol.base;

import org.traincontrol.model.ViewListener;
import java.io.Serializable; 

public abstract class NodeExpression implements Serializable 
{
    private static final long serialVersionUID = 1L;
    abstract boolean evaluate(ViewListener network);
}
