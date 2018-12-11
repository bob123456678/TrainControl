/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package layout;

/**
 *
 * @author Adam
 */
public class Layout
{
  /*  __slots__ = ('control','ps','adjacency','es','mutex')
    
    def __init__(self, control):
        """
            control : reference to rocrail interface
            ps : list of endpoints
            adjacency : adjacency list
            es : list of edges
        """
        
        self.control = control
        
        self.ps = {}
        self.adjacency = {}
        self.es = {}
        self.mutex = False
        
    def getEdge(self, e1, e2):
        """
            Alias for self.es[e]
        """
        return self.es[e1 + '-' + e2]
        
    def createPoint(self, name, isDest = True, s88 = None):
        """
            Adds a Point to the graph
        """
        
        newPoint = point(isDest, name, s88)
        
        self.ps[newPoint.name] = newPoint        
        
    def createEdge(self, startPoint, endPoint, configureFunc = None):
        """
            Adds an Edge to the graph and updates adjacency list
        """
        
        startPoint = self.ps[startPoint]
        endPoint = self.ps[endPoint]
        
        newEdge = edge(self.control, startPoint, endPoint, configureFunc)
            
        self.es[newEdge.name] = newEdge
        
        # Add edge to adjacency list
        if newEdge.startPoint.name not in self.adjacency.keys():
            self.adjacency[newEdge.startPoint.name] = set([newEdge])
        else:
            self.adjacency[newEdge.startPoint.name].add(newEdge) 
            
    def getNeighbors(self, point):
        """
            Gets the neighbors of a given point
        """
        
        neighbors = []
        
        if (point.name in self.adjacency.keys()):
            
            for edge in self.adjacency[point.name]:
                # An edge is valid only if it is not occupied, the endpoint is not occupied, and the endpoint isn't the same as the start
                if not edge.isOccupied() and edge.endPoint != point and not edge.endPoint.isOccupied():
                    neighbors.append(edge)
            
        return neighbors
              
    def configureAndLockPath(self, path):
        """
            Marks all the edges in a path as occupied
        """
        
        while self.mutex != False:
            pass
        
        # Return if this path isn't clear
        for edge in path:
            
            if (edge.isOccupied()):    
                self.mutex = False    
                return False
        
        # Configure the path
        for edge in path:
            
            edge.configure()
            edge.setOccupied()
                            
        self.mutex = False              
        return True
            
    def unlockPath(self, path):
        """
            Marks all the edges in a path as unoccupied
        """
        
        for edge in path:
            edge.setUnOccupied()
  
    def executePath(self, path, loc, speed = None):
        """
            Moves a train along a path
        """        
        def func(control, layout, path, loc, speed):

            result = layout.configureAndLockPath(path)
            
            if result == False:
                
                layout.log("Error: path is partially occupied")
                
            else:
                
                layout.log("Executing path %s for loc %s" % (path, loc))
            
                path[-1].endPoint.setCurrentLoc(loc)
    
                if (speed != None):
                    loc.setV(speed)
                else:
                    loc.setV(loc.getVar('tspeed'))
                
                control.waitForFeedback(path[-1].endPoint.s88)
                
                loc.stop()
                
                layout.unlockPath(path)
                
                path[0].startPoint.setCurrentLoc(None)
                
                layout.log("Finished path %s for loc %s" % (path, loc))

        def simulateFeedback(control, layout, path):
            
            layout.log("Simulating feedback...") 
            
            control.sleep(random.randint(5,10))
            
            control.getFeedback(path[-1].endPoint.s88).setAttr('state', 'true')

            layout.log("Feedback %s sent" % (path[-1].endPoint.s88)) 

        # Main exec (parallel)         
        self.control.run(func, self, path, loc, speed)      
            
        #self.control.run(simulateFeedback, self, path)   
        
    def log(self, text):
        """
            Logs a message
        """
        
        print "Layout: " + str(text)   
            
    def bfs(self, start, end):
        """
            Searches the graph for a path from start to end
        """
        
        start = self.ps[start]
        end = self.ps[end]
        
        if not end.isDestination or end.isOccupied():
            return False
        
        visited = []
        q = [(start, [])]
        
        while len(q) > 0:
            (point, path) = q.pop(0)
            
            visited.append(point)
            
            for next in self.getNeighbors(point):
            
                if next.endPoint == end:
                    return path + [next]
                elif next not in visited:
                    q.append((next.endPoint, path + [next]))
                    
        return False */
}
