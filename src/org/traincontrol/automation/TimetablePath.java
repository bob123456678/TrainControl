package org.traincontrol.automation;

import org.traincontrol.base.Locomotive;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.json.JSONArray;
import org.json.JSONObject;
import org.traincontrol.model.ViewListener;
import org.traincontrol.util.I18n;

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
    
    // MILLISECONDS to the next execution, despite the name (X8-C4).  Precalculated externally.
    //
    // Every writer and reader uses milliseconds: `Layout` stores a difference of two
    // `System.currentTimeMillis()` stamps and compares it against another one, `TrainControlUI`
    // multiplies the operator's typed seconds by 1000 on the way in and divides by 1000 to display it,
    // and `testLayoutTimetable` asserts 10000 for a ten-second gap.  The name and this comment were
    // the only two things in the chain that said seconds.
    //
    // Renaming it would touch nine call sites and every one of them is right; what was wrong is that
    // the field lied to the tenth person to read it.
    private long secondsToNext = 0;

    // AN ENTRY THE RAILWAY CANNOT BUILD, kept as it was written (Adam, 2026-09-30: "Just keep the entries, and if a path
    // is run that contains a point on a disabled page, reject it with an error as we do with other autonomy paths"): a
    // Point on a page left out of autonomy is not on the railway, so its path has no edges.  Dropped at the load, the
    // next fold wrote the shorter timetable over the configuration and the entry was gone for good.
    private final JSONObject unbuilt;
    private final String whyNot;

    // WHAT THE ARRIVAL WAS TOLD, for an entry recorded by hand (RSA49-B1): whether the train was turned at the end of
    // its path, or null for one recorded from autonomy or written before this was kept.  Played back, it is answered
    // again, so the train ends facing and standing as it did when it was recorded.
    private Boolean turnAtTheEnd = null;

    public TimetablePath(Locomotive loc, List<Edge> path, long executionTime)
    {
        this.loc = loc;
        this.path = path;
        this.unbuilt = null;
        this.whyNot = null;
        setExecutionTime(executionTime);
    }

    /**
     * An entry as written, whose path the railway cannot build.
     */
    private TimetablePath(Locomotive loc, JSONObject written, String whyNot, long executionTime)
    {
        this.loc = loc;
        this.path = new ArrayList<>();
        this.unbuilt = written;
        this.whyNot = whyNot;
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

    /**
     * Whether the train was turned at the end of this path when it was recorded by hand (RSA49-B1).
     *
     * @return the answer, or null for an entry recorded from autonomy or before this was kept
     */
    public Boolean getTurnAtTheEnd()
    {
        return turnAtTheEnd;
    }

    /**
     * Records whether the train was turned at the end of this path.
     *
     * @param turnAtTheEnd the answer, or null
     */
    public void setTurnAtTheEnd(Boolean turnAtTheEnd)
    {
        this.turnAtTheEnd = turnAtTheEnd;
    }

    /**
     * Whether the railway can run this entry.
     *
     * @return whether it can
     */
    public boolean isRunnable()
    {
        return this.unbuilt == null;
    }

    /**
     * Why the railway cannot run this entry, or null.
     *
     * @return the reason
     */
    public String whyNotRunnable()
    {
        return this.whyNot;
    }

    /**
     * The name of the Point this entry starts at.
     *
     * @return the name
     */
    public String getStartName()
    {
        if (this.unbuilt == null) return getStart().getName();

        JSONArray legs = this.unbuilt.optJSONArray("path");

        JSONObject first = legs == null ? null : legs.optJSONObject(0);

        return first == null ? null : first.optString("start", null);
    }

    /**
     * The name of the Point this entry ends at.
     *
     * @return the name
     */
    public String getEndName()
    {
        if (this.unbuilt == null) return getEnd().getName();

        JSONArray legs = this.unbuilt.optJSONArray("path");

        JSONObject last = legs == null ? null : legs.optJSONObject(legs.length() - 1);

        return last == null ? null : last.optString("end", null);
    }
    
    public Point getStart()
    {
        return this.path.isEmpty() ? null : this.path.get(0).getStart();
    }
    
    public Point getEnd()
    {
        return this.path.isEmpty() ? null : this.path.get(this.path.size() - 1).getEnd();
    }
    
    @Override
    public String toString()
    {
        return I18n.f("autolayout.locFromStartToEnd",
            this.loc.getName(),
            this.getStartName(),
            this.getEndName()
        );
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
        hash = 29 * hash + Objects.hashCode(this.unbuilt == null ? null : this.unbuilt.toString());
        hash = 29 * hash + Objects.hashCode(this.turnAtTheEnd);
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
        if (!Objects.equals(this.unbuilt == null ? null : this.unbuilt.toString(),
            other.unbuilt == null ? null : other.unbuilt.toString()))
        {
            return false;
        }
        if (!Objects.equals(this.turnAtTheEnd, other.turnAtTheEnd))
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
        // AS IT WAS WRITTEN, where the railway cannot build it - for its locomotive as it is called now
        if (this.unbuilt != null)
        {
            JSONObject written = new JSONObject(this.unbuilt.toString());
            written.put("loc", loc.getName());
            written.put("executionTime", this.executionTime);
            written.put("secondsToNext", this.secondsToNext);
            return written;
        }

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

        // Only where it was recorded: an entry without it is played back as autonomy, as every entry was before
        if (this.turnAtTheEnd != null) json.put("turnAtTheEnd", this.turnAtTheEnd.booleanValue());

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
    public static TimetablePath fromJSON(String jsonString, ViewListener model, Layout layout) throws Exception
    {
        JSONObject json = new JSONObject(jsonString);
        
        // Parse Locomotive
        Locomotive loc = model.getLocByName(json.getString("loc"));
        
        if (loc == null)
        {
            throw new Exception(
                I18n.f("autolayout.errorLocomotiveDoesNotExist", json.getString("loc"))
            );
        }
        
        // Parse path
        JSONArray pathArray = json.getJSONArray("path");
        List<Edge> path = new ArrayList<>();

        // A PATH THE RAILWAY CANNOT BUILD keeps the entry, as written, to be refused when it is run (Adam, 2026-09-30)
        String whyNot = null;

        for (int i = 0; i < pathArray.length() && whyNot == null; i++)
        {
            JSONObject edgeJson = pathArray.getJSONObject(i);

            String start = edgeJson.getString("start");
            String end = edgeJson.getString("end");

            Edge newEdge = layout.getEdge(start, end);

            if (newEdge == null)
            {
                String missing = layout.getPoint(start) == null ? start : layout.getPoint(end) == null ? end : null;

                whyNot = missing != null ? I18n.f("autolayout.errorPointNotOnTheRailway", missing)
                    : I18n.f("autolayout.errorEdgeDoesNotExist", start, end);
            }

            path.add(newEdge);
        }

        // A ROUTE THAT ENDS WHERE IT STARTS - a lap - is kept, and refused when it is run (RSA30-C2): no door offers one,
        // a file can carry one, and run, it left its train on no Point
        if (whyNot == null && !path.isEmpty() && path.get(0).getStart() == path.get(path.size() - 1).getEnd())
        {
            whyNot = I18n.f("autolayout.errorPathEndsWhereItStarts", path.get(0).getStart().getName());
        }

        // Parse executionTime
        long executionTime = json.getLong("executionTime");

        TimetablePath ttp = whyNot == null ? new TimetablePath(loc, path, executionTime)
            : new TimetablePath(loc, json, whyNot, executionTime);
        
        if (json.has("secondsToNext"))
        {
            ttp.setSecondsToNext(json.getLong("secondsToNext"));
        }

        // WHAT THE ARRIVAL WAS TOLD, where it was recorded by hand (RSA49-B1)
        if (json.has("turnAtTheEnd"))
        {
            ttp.setTurnAtTheEnd(json.getBoolean("turnAtTheEnd"));
        }
        
        return ttp;
    }
}
