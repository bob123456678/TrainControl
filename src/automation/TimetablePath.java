package automation;

import base.Locomotive;
import java.util.List;

/**
 * Class representing a path (locomotive and series of edges)
 * Used for execution history
 * @author adam
 */
public class TimetablePath
{
    private final Locomotive loc;
    private final List<Edge> path;
    private long executionTime;

    public TimetablePath(Locomotive loc, List<Edge> path, long executionTime)
    {
        this.loc = loc;
        this.path = path;
        setExecutionTime(executionTime);
    }

    public Locomotive getLoc()
    {
        return loc;
    }
    
    public long getExecutionTime()
    {
        return executionTime;
    }
    
    public final void setExecutionTime(long executionTime)
    {
        this.executionTime = Math.abs(executionTime);
    }

    public boolean isExecuted()
    {
        return executionTime > 0;
    }

    public List<Edge> getPath()
    {
        return path;
    }
    
    public Point getStart()
    {
        return this.path.get(0).getStart();
    }
    
    public Point getEnd()
    {
        return this.path.get(this.path.size() - 1).getEnd();
    }
    
    @Override
    public String toString()
    {
        return this.loc.getName() + " from " + this.getStart().getName() + " to " + this.getEnd().getName();
    }
}
