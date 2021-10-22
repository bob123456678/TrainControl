/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package layout;

/**
 * TODO - Old code to represent layout as a graph, convert to Java
 * @author Adam
 */
public class Edge
{
    /*
    __slots__ = ('control','name','configureFunc','startPoint','endPoint','occupied');
    
    def __init__(self, control, startPoint, endPoint, configureFunc):
        """
            control : reference to rocrail interface
            configureFunc : function to configure the track such that this edge connects points as expected
            startPoint : point at the start
            endPoint : point at the end
        """
        
        self.control = control
        self.configureFunc = configureFunc
        self.startPoint = startPoint
        self.endPoint = endPoint
        self.name = startPoint.name + '-' + endPoint.name
        
        self.setUnOccupied()
        
    def __str__(self):
        """
            Pretty printing
        """
        
        return "%s" % (self.name)
    
    def __repr__(self):
        
        return self.__str__()
    
    def __eq__(self, other):
        """
            Check for equivalence
        """
        
        return self.name == other.name
        
    def configure(self):
        """
            Fires the configuration function
        """    
        
        if self.configureFunc != None and not self.isOccupied():
            self.configureFunc(self.control)
       
    def isOccupied(self):
        """
            Returns true if a train currently occupies or may occupy this edge
        """
        
        return self.occupied
    
    def setOccupied(self):
        """
            Marks the edge as occupied
        """
        
        self.occupied = True
        
    def setUnOccupied(self):
        """
            Marks the edge as clear
        """
        
        self.occupied = False*/
}
            
