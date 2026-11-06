package org.traincontrol.gui;

import java.awt.Component;
import javax.swing.JOptionPane;
import org.traincontrol.base.Locomotive;
import org.traincontrol.util.I18n;

/**
 * The doors to the Locomotive Settings window and the one rule they share: a locomotive is not edited while the power
 * is on and it, or a multi-unit it belongs to, is running (FR-048).  Every door and control asks {@link #whyNotEditable}.
 */
public final class LocomotiveSettings
{
    private LocomotiveSettings()
    {
    }

    /**
     * Whether the power is on and the locomotive, or a multi-unit it belongs to, has a speed above zero.
     *
     * @param ui the main window
     * @param l the locomotive
     */
    public static boolean isMoving(TrainControlUI ui, Locomotive l)
    {
        if (ui == null || l == null || ui.getModel() == null || !ui.powerIsOn()) return false;

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
     * The sentence that says a locomotive cannot be edited now.
     *
     * @return null when it can be, else "Stop BR 218 to edit it."
     */
    public static String whyNotEditable(TrainControlUI ui, Locomotive l)
    {
        return isMoving(ui, l) ? I18n.f("loc.ui.stopToEdit", l.getName()) : null;
    }

    /**
     * Asks the rule and, when it says no, says so.
     *
     * @param owner what the message hangs off
     * @return true when the locomotive is moving and the message has been shown
     */
    public static boolean refuseIfMoving(TrainControlUI ui, Locomotive l, Component owner)
    {
        String why = whyNotEditable(ui, l);

        if (why == null) return false;

        JOptionPane.showMessageDialog(owner == null ? ui : owner, why);

        return true;
    }
}
