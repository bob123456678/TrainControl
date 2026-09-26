package org.traincontrol.model;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.Feedback;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.Locomotive.decoderType;
import org.traincontrol.base.NodeExpression;
import org.traincontrol.base.RenameProposals;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.Route;
import org.traincontrol.base.udp.CANMessage;

/**
 * Model functionality in the eyes of the GUI
 * @author Adam
 */
public interface ViewListener
{
    public void go();
    public void stop();
    public void showAutonomyAlert(String message);
    public List<String> getLocList();
    public List<String> getRouteList();
    public Locomotive getLocByName(String name);
    public Accessory getAccessoryByName(String name);
    public void saveState(boolean backup);
    public Locomotive newMM2Locomotive(String name, int address);
    public Locomotive newMFXLocomotive(String name, int address);
    public Locomotive newDCCLocomotive(String name, int address);
    public boolean deleteLoc(String name);
    public String getLocAddress(String name);
    public boolean renameLoc(String oldName, String newName);
    public void setAccessoryState(int address, Accessory.accessoryDecoderType decoderType, boolean state);
    public void execRoute(String name);
    public void deleteRoute(String name);
    public boolean getAccessoryState(int address, Accessory.accessoryDecoderType decoderType);

    /**
     * The same state, WITHOUT registering an accessory that is not there (C13).
     *
     * `getAccessoryState` creates a switch on a miss.  That is deliberate on a command path and wrong
     * on every path that is only looking: evaluating a route condition, or painting a keyboard key,
     * registered every address it asked about and the invented accessories persisted.
     *
     * The ANSWER is identical - `getAccessoryState` creates the switch unswitched and then returns
     * false, so an absent accessory has always read as not switched.  Only the side effect differs.
     *
     * @param address
     * @param decoderType
     * @return whether the accessory is switched; false when there is no such accessory
     */
    public boolean getAccessoryStateIfPresent(int address, Accessory.accessoryDecoderType decoderType);
    public Accessory getAccessoryByAddress(int address, Accessory.accessoryDecoderType decoderType);

    /**
     * Returns the accessory at the given address, or null if none exists.  Unlike
     * getAccessoryByAddress, this never creates one - use it on display and read-only paths
     * @param address
     * @param decoderType
     * @return
     */
    public Accessory getAccessoryByAddressIfPresent(int address, Accessory.accessoryDecoderType decoderType);
    public boolean getPowerState();
    public void allFunctionsOff();
    public void locFunctionsOff(Locomotive l);
    public void lightsOn(List<String> locomotives);
    public void log(String s);
    public void log(Exception e);
    public void stopAllLocs();
    public int syncWithCS2();
    public List<String> getLayoutList();
    public LayoutDiagram getLayout(String name);
    public void syncLocomotive(String name);
    public boolean isFeedbackSet(String name);
    public boolean getFeedbackState(String name);
    public boolean setFeedbackState(String name, boolean state); // for simulation purposes
    public boolean isCS3();
    public String getCS3AppUrl();
    public boolean newRoute(String name, List<RouteCommand> route, int s88, Route.s88Triggers s88Trigger, boolean routeEnabled, NodeExpression conditions);
    public boolean editRoute(String name, String newName, List<RouteCommand> route, int s88, Route.s88Triggers s88Trigger, boolean routeEnabled, NodeExpression conditions);
    public Route getRoute(String name);
    public Route getRoute(int id);
    public int getRouteId(String name);
    public Map<Integer, Set<Locomotive>> getDuplicateLocAddresses();

    /**
     * Every locomotive address in use, mapped to the locomotives using it.
     * @return
     */
    public Map<Integer, Set<Locomotive>> getLocAddresses();
    public void parseAuto(String s);
    public void applyAutonomyRouteActivations();
    public Layout getAutoLayout();
    public boolean hasAutoLayout();

    /**
     * Forgets the automation graph, so that nothing is loaded.  Everything else here either replaces one
     * graph with another or creates one on demand, so without this a layout given a configuration could
     * never be returned to having none.
     */
    public void clearAutoLayout();

    /**
     * Reloads the track diagrams only, without re-importing routes or locomotives
     */
    public void refreshLayouts();
    /**
     * The route now executing that drives this locomotive, or null (CS3-B1).
     *
     * Asked by the doors that edit or delete a locomotive: a route part-way through its commands is about to send
     * this one a speed, and a route runs by hand or from an s88 with autonomy idle, so `isAutonomyRunning` does not
     * cover it.
     *
     * @param name the locomotive
     * @return the route, or null when no running route names it
     */
    public Route runningRouteDriving(String name);

    public boolean isAutonomyRunning();
    public boolean isDebug();
    Accessory newSignal(int address, Accessory.accessoryDecoderType decoderType, boolean state);
    Accessory newSwitch(int address, Accessory.accessoryDecoderType decoderType, boolean state);
    public boolean getNetworkCommState();

    /**
     * Whether this session is simulating rather than driving a real Central Station.
     * @return
     */
    public boolean isSimulation();
    public int getNumMessagesProcessed();
    public boolean changeRouteId(String name, int newId);
    public void clearLayouts();
    public String exportRoutes() throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, Exception;
    /**
     * Replaces every route with those in a route export, each arriving with its automatic firing off.
     *
     * @param json the export
     * @return how many routes were added
     */
    public int importRoutes(String json);

    /**
     * The same, turning automatic firing back on for the routes the file saved with it on, when the operator said so
     * (REG2-C7).
     *
     * @param json the export
     * @param armAsSaved whether a route saved armed arrives armed
     * @return how many routes were added
     */
    public int importRoutes(String json, boolean armAsSaved);

    /**
     * The routes a route export saved with their automatic firing on and a sensor to watch, by name (REG2-C7, RLU-C2) -
     * the ones the import arms again when asked, and what its door asks about before anything is replaced.
     *
     * @param json the export
     * @return their names, empty when there are none or the file cannot be read
     */
    public List<String> routesSavedArmed(String json);

    /**
     * The routes the last import split because each put an emergency stop among other commands, each with the stop route
     * it now fires in the stop's place (Adam, 2026-09-25).
     *
     * @return pairs of route and stop route; empty when the last import split nothing
     */
    public List<String[]> getRoutesSplitByLastImport();
    public List<Locomotive> getLocomotives();
    public void changeLocAddress(String locName, int newAddress, decoderType newDecoderType) throws Exception;
    public void sendPing(boolean force);
    public long getTimeSinceLastPing();
    public TreeMap<String, Long> getDailyRuntimeStats(int days, long offset);
    public TreeMap<String, Integer> getDailyCountStats(int days, long offset);
    public int getTotalLocStats(int days, long offset);
    public Locomotive isLocLinkedToOthers(Locomotive l);
    public void waitForPowerState(boolean state) throws InterruptedException;

    /**
     * The same, with a deadline.
     *
     * @param state the state to wait for
     * @param timeoutMs how long to allow the Central Station to answer
     * @return whether the state was reached
     * @throws InterruptedException
     */
    public boolean waitForPowerState(boolean state, long timeoutMs) throws InterruptedException;
    public void downloadLayout(File path) throws Exception;
    public List<String[]> getLocomotivesToRenameFromImport() throws Exception;

    /**
     * The rename check with its refusal count, so an empty proposal list can be told apart from
     * everything having been declined
     * @return
     * @throws java.lang.Exception
     */
    public RenameProposals getRenameProposals() throws Exception;
    public String exportLocsToCSV();
    public void logf(String key, Object... args);
    public Feedback newFeedback(int id, CANMessage message);
}
