package org.traincontrol.base;

import org.traincontrol.model.ViewListener;

public class NodeRouteCommand extends NodeExpression
{
    private final RouteCommand command;

    public NodeRouteCommand(RouteCommand command)
    {
        this.command = command;
    }
    
    public RouteCommand getRouteCommand()
    {
        return command;
    }

    @Override
    boolean evaluate(ViewListener network)
    {
        return Route.evaluate(command, network);
    }
}

