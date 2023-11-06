package model;

import automation.Layout;
import base.RouteCommand;
import java.util.List;
import java.util.Map;
import java.util.Set;
import marklin.MarklinAccessory;
import marklin.MarklinLayout;
import marklin.MarklinLocomotive;
import marklin.MarklinRoute;

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
    public MarklinLocomotive getLocByName(String name);
    public MarklinAccessory getAccessoryByName(String name);
    public void saveState();
    public MarklinLocomotive newMM2Locomotive(String name, int address);
    public MarklinLocomotive newMFXLocomotive(String name, int address);
    public MarklinLocomotive newDCCLocomotive(String name, int address);
    public boolean deleteLoc(String name);
    public String getLocAddress(String name);
    public boolean renameLoc(String oldName, String newName);
    public void setAccessoryState(int address, boolean state);
    public void execRoute(String name);
    public void deleteRoute(String name);
    public boolean getAccessoryState(int address);
    public boolean getPowerState();
    public void allFunctionsOff();
    public void locFunctionsOff(MarklinLocomotive l);
    public void lightsOn(List<String> locomotives);
    public void log(String s);
    public void stopAllLocs();
    public int syncWithCS2();
    public List<String> getLayoutList();
    public MarklinLayout getLayout(String name);
    public void syncLocomotive(String name);
    public boolean isFeedbackSet(String name);
    public boolean getFeedbackState(String name);
    public boolean setFeedbackState(String name, boolean state); // for simulation purposes
    public boolean isCS3();
    public String getCS3AppUrl();
    public boolean newRoute(String name, List<RouteCommand> route, int s88, MarklinRoute.s88Triggers s88Trigger, boolean routeEnabled, Map<Integer, Boolean> conditionS88s, List<RouteCommand> conditionAccessories);
    public void editRoute(String name, String newName, List<RouteCommand> route, int s88, MarklinRoute.s88Triggers s88Trigger, boolean routeEnabled, Map<Integer, Boolean> conditionS88s, List<RouteCommand> conditionAccessories);
    public MarklinRoute getRoute(String name);
    public int getRouteId(String name);
    public Map<Integer, Set<MarklinLocomotive>> getDuplicateLocAddresses();
    public void parseAuto(String s);
    public Layout getAutoLayout();
    public boolean isDebug();
    MarklinAccessory newSignal(String name, int address, boolean state);
    MarklinAccessory newSwitch(String name, int address, boolean state);
    public boolean getNetworkCommState();
    public int getNumMessagesProcessed();
    public boolean changeRouteId(String name, int newId);
    public void clearLayouts();
    public String exportRoutes() throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException;
    public void importRoutes(String json);
    public List<MarklinLocomotive> getLocomotives();
    public void changeLocAddress(String locName, int newAddress) throws Exception;
}
