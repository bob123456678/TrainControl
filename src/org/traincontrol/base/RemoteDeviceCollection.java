package org.traincontrol.base;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * A generic collection of items with unique string names
 * @author Adam
 * @param <ITEM>
 * @param <IDENTIFIER> 
 */
public class RemoteDeviceCollection<ITEM, IDENTIFIER> implements
    java.io.Serializable
{
    // Device database
    private final HashMap<IDENTIFIER, ITEM> db;
    
    // Device name map
    private final HashMap<String, IDENTIFIER> names;
    
    /**
     * Constructor
     */
    public RemoteDeviceCollection()
    {
        this.db = new HashMap<>();
        this.names = new HashMap<>();
    }
    
    /**
     * Adds a device.
     *
     * Maintains a strict one-to-one mapping between names and ids: adding clears out anything the
     * name or the id was previously attached to, so neither map can accumulate entries the other
     * does not know about.
     * @param device
     * @param name
     * @param id
     */
    public void add(ITEM device, String name, IDENTIFIER id)
    {
        IDENTIFIER existingId = this.names.get(name);

        // Re-adding a name under a different id would otherwise strand the old device in the
        // database, where it would still be returned by getItems but not by getItemNames
        if (existingId != null && !existingId.equals(id))
        {
            this.db.remove(existingId);
        }

        // Re-adding an id under a different name would otherwise strand the old name, which would
        // keep being listed by getItemNames and keep resolving - to the new device - via getByName.
        // An accessory re-created as the other type is the case that matters: a switch and a signal
        // at one address share an id, so "Switch 5" would linger after it became "Signal 5".
        this.names.values().removeIf(mapped -> mapped != null && mapped.equals(id));

        this.db.put(id, device);
        this.names.put(name, id);
    }
    
    /**
     * Does the given name exist in the DB?
     * @param name
     * @return 
     */
    public boolean hasName(String name)
    {
        return this.names.containsKey(name);
    }
    
    /**
     * Does the given id exist in the DB?
     * @param id
     * @return 
     */
    public boolean hasId(IDENTIFIER id)
    {
        return this.db.containsKey(id);
    }
    
    /**
     * Gets a device by name
     * @param name
     * @return 
     */
    public ITEM getByName(String name)
    {
        return this.db.get(this.names.get(name));                
    }
    
    /**
     * Gets a device by id
     * @param id
     * @return 
     */
    public ITEM getById(IDENTIFIER id)
    {
        return this.db.get(id);                
    }
    
    /**
     * Returns all existing device ids
     * @return 
     */
    public List<IDENTIFIER> getItemIds()
    {
        List<IDENTIFIER> l = new LinkedList<>();
        l.addAll(this.db.keySet());
        
        return l; 
    }
    
    /**
     * Gets all existing device names
     * @return 
     */
    public List<String> getItemNames()
    {
        List<String> l = new LinkedList<>();
        l.addAll(this.names.keySet());
        
        return l;
    }
    
    /**
     * Gets all existing devices
     * @return 
     */
    public List<ITEM> getItems()
    {
        List<ITEM> l = new LinkedList<>();
        
        for(IDENTIFIER k : this.db.keySet())
        {
            l.add(this.db.get(k));
        }
        
        return l;
    }
    
    /**
     * Removes the specified name from the database
     * @param name
     * @return 
     */
    public boolean delete(String name)
    {
        if (this.hasName(name))
        {
            IDENTIFIER id = this.names.get(name);
            
            this.db.remove(id);
            this.names.remove(name);
            
            return true;
        }
        
        return false;
    }
}
