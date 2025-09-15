package org.traincontrol.base;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * This class wraps rich notes for a locomotive
 */
public class LocomotiveNotes
{
    private final int startYear;
    private final int endYear;
    private final String railway;
    private final String notes;

    public LocomotiveNotes(int startYear, int endYear, String railway, String notes)
    {
        this.startYear = startYear;
        this.endYear = endYear;
        this.railway = railway;
        this.notes = notes;
    }

    public JSONObject toJson()
    {
        JSONObject obj = new JSONObject();
        obj.put("startYear", startYear);
        obj.put("endYear", endYear);
        obj.put("railway", railway);
        obj.put("notes", notes);
        return obj;
    }

    public static LocomotiveNotes fromJson(String jsonString)
    {
        try
        {
            JSONObject obj = new JSONObject(jsonString);
            return new LocomotiveNotes(
                obj.optInt("startYear", 0),
                obj.optInt("endYear", 0),
                obj.optString("railway", ""),
                obj.optString("notes", "")
            );
        }
        catch (JSONException e)
        {
            // Fallback for legacy plain text
            return new LocomotiveNotes(0, 0, "", jsonString);
        }
    }

    public int getStartYear()
    {
        return startYear;
    }

    public int getEndYear()
    {
        return endYear;
    }

    public String getRailway()
    {
        return railway;
    }

    public String getNotes()
    {
        return notes;
    }
}