package org.traincontrol.gui;

import java.awt.Component;
import javax.swing.JOptionPane;
import org.traincontrol.base.Locomotive;
import org.traincontrol.util.I18n;

/**
 * The one rule behind the Locomotive Settings window (FR-048): a locomotive is not edited while it, or a multi-unit it
 * belongs to, is moving with the power on, or while autonomy runs.  Every door opens the window anyway, locked; its
 * strip, Save, trash can and picture marks, and every write and delete ask {@link #whyNotEditable}; the quick actions
 * (the presets) do not.
 */
public final class LocomotiveSettings
{
    private LocomotiveSettings()
    {
    }

    /** Why a locomotive cannot be edited. */
    public enum Why
    {
        MOVING, AUTONOMY
    }

    /** Whether the power is on and the locomotive, or a multi-unit it belongs to, has a speed above zero. */
    public static boolean isMoving(TrainControlUI ui, Locomotive l)
    {
        return ui != null && ui.powerIsOn() && hasSpeed(ui, l);
    }

    /**
     * Whether the locomotive, or a multi-unit it belongs to, has a speed above zero, with the power on or off: on Go the
     * decoder at the old address would resume it.
     */
    public static boolean hasSpeed(TrainControlUI ui, Locomotive l)
    {
        if (ui == null || l == null || ui.getModel() == null) return false;

        if (l.getSpeed() > 0) return true;

        for (Locomotive other : ui.getModel().getLocomotives())
        {
            if (other == l || other.getSpeed() <= 0) continue;

            if (other.isLinkedTo(l)) return true;

            java.util.Map<String, Double> held = other.getDecoderType() == Locomotive.decoderType.MULTI_UNIT
                ? other.getModelMultiUnitLocomotiveNames() : null;

            if (held != null && held.containsKey(l.getName())) return true;
        }

        return false;
    }

    /**
     * Why a locomotive cannot be edited now: autonomy is running (for every locomotive), or it, or a multi-unit it
     * belongs to, is moving with the power on.  A route that drives it is not a reason: its name, address and multi-unit
     * are refused at Save.
     *
     * @return the reason, or null when it can be edited
     */
    public static Why why(TrainControlUI ui, Locomotive l)
    {
        if (ui == null || l == null || ui.getModel() == null) return null;

        if (isMoving(ui, l)) return Why.MOVING;

        return ui.getModel().isAutonomyRunning() ? Why.AUTONOMY : null;
    }

    /**
     * The sentence that says a locomotive cannot be edited now.
     *
     * @return null when it can be, else "Stop BR 218 to edit it." or the refusal for autonomy
     */
    public static String whyNotEditable(TrainControlUI ui, Locomotive l)
    {
        return say(ui, l, false);
    }

    /** What the window's strip says for the reason, or null when the locomotive can be edited. */
    public static String stripFor(TrainControlUI ui, Locomotive l)
    {
        return say(ui, l, true);
    }

    private static String say(TrainControlUI ui, Locomotive l, boolean strip)
    {
        Why why = why(ui, l);

        if (why == null) return null;

        if (why == Why.MOVING) return I18n.f(strip ? "loc.ui.settingsMovingStrip" : "loc.ui.stopToEdit", l.getName());

        return I18n.t(strip ? "loc.ui.settingsAutonomyStrip" : "autolayout.ui.errorCannotEditLocomotivesWhileRunning");
    }

    /**
     * Asks the rule and, when it says no, says so.
     *
     * @param owner what the message hangs off
     * @return true when the locomotive cannot be edited and the message has been shown
     */
    public static boolean refuseIfNotEditable(TrainControlUI ui, Locomotive l, Component owner)
    {
        String why = whyNotEditable(ui, l);

        if (why == null) return false;

        JOptionPane.showMessageDialog(owner == null ? ui : owner, why);

        return true;
    }
}
