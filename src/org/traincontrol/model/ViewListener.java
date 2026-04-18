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
import org.traincontrol.base.RouteCommand;
import org.traincontrol.base.TrackLayout;
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
    public Accessory getAccessoryByAddress(int address, Accessory.accessoryDecoderType decoderType);
    public boolean getPowerState();
    public void allFunctionsOff();
    public void locFunctionsOff(Locomotive l);
    public void lightsOn(List<String> locomotives);
    public void log(String s);
    public void log(Exception e);
    public void stopAllLocs();
    public int syncWithCS2();
    public List<String> getLayoutList();
    public TrackLayout getLayout(String name);
    public void syncLocomotive(String name);
    public boolean isFeedbackSet(String name);
    public boolean getFeedbackState(String name);
    public boolean setFeedbackState(String name, boolean state); // for simulation purposes
    public boolean isCS3();
    public String getCS3AppUrl();
    public boolean newRoute(String name, List<RouteCommand> route, int s88, Route.s88Triggers s88Trigger, boolean routeEnabled, NodeExpression conditions);
    public void editRoute(String name, String newName, List<RouteCommand> route, int s88, Route.s88Triggers s88Trigger, boolean routeEnabled, NodeExpression conditions);
    public Route getRoute(String name);
    public Route getRoute(int id);
    public int getRouteId(String name);
    public Map<Integer, Set<Locomotive>> getDuplicateLocAddresses();
    public void parseAuto(String s);
    public void applyAutonomyRouteActivations();
    public Layout getAutoLayout();
    public boolean hasAutoLayout();
    public boolean isAutonomyRunning();
    public boolean isDebug();
    Accessory newSignal(int address, Accessory.accessoryDecoderType decoderType, boolean state);
    Accessory newSwitch(int address, Accessory.accessoryDecoderType decoderType, boolean state);
    public boolean getNetworkCommState();
    public int getNumMessagesProcessed();
    public boolean changeRouteId(String name, int newId);
    public void clearLayouts();
    public String exportRoutes() throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, Exception;
    public void importRoutes(String json);
    public List<Locomotive> getLocomotives();
    public void changeLocAddress(String locName, int newAddress, decoderType newDecoderType) throws Exception;
    public void sendPing(boolean force);
    public long getTimeSinceLastPing();
    public TreeMap<String, Long> getDailyRuntimeStats(int days, long offset);
    public TreeMap<String, Integer> getDailyCountStats(int days, long offset);
    public int getTotalLocStats(int days, long offset);
    public Locomotive isLocLinkedToOthers(Locomotive l);
    public void waitForPowerState(boolean state) throws InterruptedException;
    public void downloadLayout(File path) throws Exception;
    public List<String[]> getLocomotivesToRenameFromImport() throws Exception;
    public String exportLocsToCSV();
    public void logf(String key, Object... args);
    public Feedback newFeedback(int id, CANMessage message);
}
