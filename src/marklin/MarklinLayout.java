package marklin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Layout container with grid and size info
 * @author Adam
 */
public class MarklinLayout
{
    private final String name;
    
    // Size
    private final int sx;
    private final int sy;
    
    int minx = 0;
    int miny = 0;
    int maxx;
    int maxy;

    public int getMinx()
    {
        return minx;
    }

    public int getMiny()
    {
        return miny;
    }

    public int getMaxx()
    {
        return maxx;
    }

    public int getMaxy()
    {
        return maxy;
    }
   
    // Corresponding accessory reference
    private final List<List<MarklinLayoutComponent>> grid;
    
     // Network reference
    private final MarklinControlStation network;
   
    /**
     * Constructor
     * @param name
     * @param sx size x
     * @param sy size y
     * @param network 
     */
    public MarklinLayout(String name, int sx, int sy, MarklinControlStation network)
    {
        this.name = name;
        this.sx = sx;
        this.sy = sy;
        this.maxx = sx;
        this.maxy = sy;
        this.network = network;
        
        this.grid = new ArrayList<>();
        
        for (int i = 0; i < sx; i++)
        {            
            List<MarklinLayoutComponent> l = 
                    new ArrayList<>();
            
            for (int j = 0; j < sy; j++)
            {
                l.add(null);
            }
            
            grid.add(l);
        }
    }
    
    /**
     * Called upon user request, i.e. GUI button press
     * @param x
     * @param y
     */
    public void execStateChange(int x, int y)
    {
        // make a 
    }
    
    public void addComponent(MarklinLayoutComponent.componentType t, 
            int x, int y, int orient, int state, int address, int rawAddresss) throws IOException
    {
        assert x < sx;
        assert y < sy;
                
        grid.get(x).add(y, new MarklinLayoutComponent(t, x, y, orient, state, address, rawAddresss));
    }
    
    public void addComponent(MarklinLayoutComponent l, int x, int y) throws IOException
    {
        assert x < sx;
        assert y < sy;
                
        grid.get(x).add(y, l);
    }
    
    public String getName()
    {
        return this.name;
    }
    
    public MarklinLayoutComponent getComponent(int x, int y)
    {
        return this.grid.get(x).get(y);
    }
    
    public List<MarklinLayoutComponent> getAll()
    {
        List<MarklinLayoutComponent> out = new ArrayList<>();
        
        for (int x = 0; x < sx; x++)
        {            
            for (int y = 0; y < sy; y++)
            {
                if (this.getComponent(x, y) != null)
                {
                    out.add(this.getComponent(x, y));
                }
            }
        }
        
        return out;
    }
    
    public void checkBounds()
    {
        minx = sx;
        miny = sy;
        maxx = 0;
        maxy = 0;
        
        for (int x = 0; x < sx; x++)
        {            
            for (int y = 0; y < sy; y++)
            {
                if (this.getComponent(x, y) != null)
                {
                    if (x < minx)
                    {
                        minx = x;
                    }
                    
                    if (y < miny)
                    {
                        miny = y;
                    }
                    
                    if (x > maxx)
                    {
                        maxx = x;
                    }
                    
                    if (y > maxy)
                    {
                        maxy = y;
                    }
                }
            }            
        }  
    }

    public int getSx()
    {
        return sx;
    }

    public int getSy()
    {
        return sy;
    }
    
    public MarklinControlStation getControl()
    {
        return this.network;
    }
    
    @Override
    public String toString()
    {        
        return "Layout " + this.name + " (" + Integer.toString(sx) + "x" + Integer.toString(sy) + ")";
    }
}
