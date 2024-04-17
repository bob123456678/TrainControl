package automation;

import base.Locomotive;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import marklin.MarklinControlStation;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Class representing a path (locomotive and series of edges)
 * Used for execution history/timetables
 * @author adam
 */
public class TimetablePath
{
    private final Locomotive loc;
    private final List<Edge> path;
    private long executionTime;
    
    // Seconds to the next execution.  Precalculated externally
    private long secondsToNext = 0;

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

    public long getSecondsToNext()
    {
        return secondsToNext;
    }

    public void setSecondsToNext(long secondsToNext)
    {
        this.secondsToNext = Math.abs(secondsToNext);
    }

    @Override
    public int hashCode()
    {
        int hash = 5;
        hash = 29 * hash + Objects.hashCode(this.loc);
        hash = 29 * hash + Objects.hashCode(this.path);
        hash = 29 * hash + (int) (this.executionTime ^ (this.executionTime >>> 32));
        hash = 29 * hash + (int) (this.secondsToNext ^ (this.secondsToNext >>> 32));
        return hash;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (obj == null)
        {
            return false;
        }
        if (getClass() != obj.getClass())
        {
            return false;
        }
        
        final TimetablePath other = (TimetablePath) obj;
        
        if (this.executionTime != other.executionTime)
        {
            return false;
        }
        if (this.secondsToNext != other.secondsToNext)
        {
            return false;
        }
        
        if (!Objects.equals(this.loc, other.loc))
        {
            return false;
        }
        
        return Objects.equals(this.path, other.path);
    }
    
    /**
     * Writes a path entry to JSON
     * @return
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     * @throws NoSuchFieldException 
     */
    public JSONObject toJSON() throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException
    {
        JSONObject json = new JSONObject();
        
        json.put("loc", loc.getName());
        
        JSONArray pathArray = new JSONArray();
        
        for (Edge edge : path)
        {
            pathArray.put(edge.toSimpleJSON());
        }
        
        json.put("path", pathArray);
        
        json.put("executionTime", this.executionTime);
        json.put("secondsToNext", this.secondsToNext);

        return json;
    }
    
    /**
     * Reads a path entry from JSON
     * @param jsonString
     * @param model
     * @param layout
     * @return
     * @throws Exception 
     */
    public static TimetablePath fromJSON(String jsonString, MarklinControlStation model, Layout layout) throws Exception
    {
        JSONObject json = new JSONObject(jsonString);
        
        // Parse Locomotive
        Locomotive loc = model.getLocByName(json.getString("loc"));
        
        if (loc == null)
        {
            throw new Exception("Locomotive " + json.getString("loc") + " does not exist");
        }
        
        // Parse path
        JSONArray pathArray = json.getJSONArray("path");
        List<Edge> path = new ArrayList<>();
        for (int i = 0; i < pathArray.length(); i++)
        {
            JSONObject edgeJson = pathArray.getJSONObject(i);
                            
            Edge newEdge = layout.getEdge(edgeJson.getString("start"), edgeJson.getString("end"));
            
            if (newEdge == null)
            {
                throw new Exception("Edge " + edgeJson.getString("start") + " to " + edgeJson.getString("end") + " does not exist.");
            }
            
            path.add(newEdge);
        }
        
        // Parse executionTime
        long executionTime = json.getLong("executionTime");

        TimetablePath ttp = new TimetablePath(loc, path, executionTime);
        
        if (json.has("secondsToNext"))
        {
            ttp.setSecondsToNext(json.getLong("secondsToNext"));
        }
        
        return ttp;
    }
}
