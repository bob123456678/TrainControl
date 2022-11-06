package model;

import base.Feedback;
import java.util.List;
import marklin.MarklinAccessory;
import marklin.MarklinLayout;
import marklin.MarklinLocomotive;

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
    public boolean isCS3();
    public String getCS3AppUrl();
}
