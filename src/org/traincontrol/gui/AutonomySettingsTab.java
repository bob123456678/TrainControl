package org.traincontrol.gui;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;
import org.traincontrol.base.Route;

/**
 * The Auto tab's settings: loading every control from the railway, and the handler each control calls when it is let
 * go, which writes the setting or, while a run holds the setup, refuses and puts the tab back.  The controls are the
 * window's, reached through its accessors; every field stays on `TrainControlUI`.
 */
final class AutonomySettingsTab
{
    private final TrainControlUI ui;

    AutonomySettingsTab(TrainControlUI ui)
    {
        this.ui = ui;
    }

    /**
     * The route list, let go: arms the routes picked for autonomy, or puts the picks back when a run holds the setup.
     */
    void autoRouteListMouseReleased(java.awt.event.MouseEvent evt)
    {
        if (TrainControlUI.onAGreyedControl(evt)) return;


        javax.swing.SwingUtilities.invokeLater(() ->
            {
                if (this.ui.getModel().hasAutoLayout())
                {
                    if (!this.ui.refuseWhileARunHoldsTheSetup(this.ui, this.ui::applyAutoRouteListSelections))
                    {
                        // Collect IDs of all selected routes
                        List<Integer> activateRouteIDs = new ArrayList<>();

                        for (Route r:  this.ui.autoRouteList().getSelectedValuesList()) activateRouteIDs.add(r.getId());

                        // Push back into the model
                        this.ui.getModel().getAutoLayout().setActivateRouteIDs(activateRouteIDs);
                        this.ui.getModel().applyAutonomyRouteActivations();
                        this.ui.refreshRouteList();
                    }
                }
            });
    }

    /**
     * Activate Routes, let go: switches arming the picked routes on or off, unless a run holds the setup.
     */
    void toggleSpecifiedRoutesMouseReleased(java.awt.event.MouseEvent evt)
    {
        if (TrainControlUI.onAGreyedControl(evt)) return;

        javax.swing.SwingUtilities.invokeLater(() ->
            {
                if (!this.ui.refuseWhileARunHoldsTheSetup(this.ui))
                {
                    this.ui.getModel().getAutoLayout().setActivateRoutes(!this.ui.getModel().getAutoLayout().isActivateRoutes());
                    this.ui.getModel().applyAutonomyRouteActivations();
                }

                this.ui.toggleSpecifiedRoutes().setSelected(this.ui.getModel().getAutoLayout().isActivateRoutes());

                this.ui.refreshActivateRoutesControls();
            });
    }

    /** A setting written from its control; an exception refuses it, and its message is shown. */
    private interface Setting
    {
        void write() throws Exception;
    }

    /**
     * A setting's control, let go: a grayed control, or a write that fails, puts the tab back from the railway, and
     * nothing is written while a run holds the setup.
     */
    private void letGo(java.awt.event.MouseEvent evt, Setting setting)
    {
        if (TrainControlUI.onAGreyedControl(evt))
        {
            loadAutoLayoutSettings();
            return;
        }

        if (this.ui.isAutoLayoutRunning()) return;

        try
        {
            setting.write();
        }
        catch (Exception e)
        {
            JOptionPane.showMessageDialog(this.ui, e.getMessage());
            loadAutoLayoutSettings();
        }
    }

    /** The trains-at-once slider, let go. */
    void maxActiveTrainsMouseReleased(java.awt.event.MouseEvent evt)
    {
        letGo(evt, () -> this.ui.getModel().getAutoLayout().setMaxActiveTrains(this.ui.maxActiveTrains().getValue()));
    }

    /** The maximum latency slider, let go. */
    void maximumLatencyMouseReleased(java.awt.event.MouseEvent evt)
    {
        letGo(evt, () ->
        {
            this.ui.getModel().getAutoLayout().setMaxLatency(this.ui.maximumLatency().getValue());
            this.ui.maximumLatency().setValue(this.ui.getModel().getAutoLayout().getMaxLatency());
        });
    }

    /** The functions-on-departure box, let go. */
    void turnOnFunctionsOnDepartureMouseReleased(java.awt.event.MouseEvent evt)
    {
        letGo(evt, () -> this.ui.getModel().getAutoLayout()
            .setTurnOnFunctionsOnDeparture(this.ui.turnOnFunctionsOnDeparture().isSelected()));
    }

    /** The simulate box, let go. */
    void simulateMouseReleased(java.awt.event.MouseEvent evt)
    {
        letGo(evt, () -> this.ui.getModel().getAutoLayout().setSimulate(this.ui.simulate().isSelected()));
    }

    /** The functions-off-on-arrival box, let go. */
    void turnOffFunctionsOnArrivalMouseReleased(java.awt.event.MouseEvent evt)
    {
        letGo(evt, () -> this.ui.getModel().getAutoLayout()
            .setTurnOffFunctionsOnArrival(this.ui.turnOffFunctionsOnArrival().isSelected()));
    }

    /**
     * The atomic routes box, let go: switching it off is refused while the railway lacks the lengths that needs.
     */
    void atomicRoutesMouseReleased(java.awt.event.MouseEvent evt)
    {
        letGo(evt, () ->
        {
            // Only switching OFF can be refused (while lengths are missing); going back to atomic is always safe.
            if (!this.ui.atomicRoutes().isSelected())
            {
                String why = this.ui.whyNonAtomicRoutesAreRefused();

                if (why != null)
                {
                    JOptionPane.showMessageDialog(this.ui, why);

                    // The tick goes back, as every other refusal on this panel puts it; the setting is never
                    // touched.
                    loadAutoLayoutSettings();

                    return;
                }
            }

            this.ui.getModel().getAutoLayout().setAtomicRoutes(this.ui.atomicRoutes().isSelected());
        });
    }

    /** The pre-arrival speed reduction slider, let go. */
    void preArrivalSpeedReductionMouseReleased(java.awt.event.MouseEvent evt)
    {
        letGo(evt, () -> this.ui.getModel().getAutoLayout()
            .setPreArrivalSpeedReduction(Double.valueOf(this.ui.preArrivalSpeedReduction().getValue()) / 100));
    }

    /** The default speed slider, let go. */
    void defaultLocSpeedMouseReleased(java.awt.event.MouseEvent evt)
    {
        letGo(evt, () -> this.ui.getModel().getAutoLayout().setDefaultLocSpeed(this.ui.defaultLocSpeed().getValue()));
    }

    /** The maximum delay slider, let go. */
    void maxDelayMouseReleased(java.awt.event.MouseEvent evt)
    {
        letGo(evt, () -> this.ui.getModel().getAutoLayout().setMaxDelay(this.ui.maxDelay().getValue()));
    }

    /** The inactive-minutes slider, let go. */
    void maxLocInactiveSecondsMouseReleased(java.awt.event.MouseEvent evt)
    {
        letGo(evt, () -> this.ui.getModel().getAutoLayout()
            .setMaxLocInactiveSeconds(this.ui.maxLocInactiveSeconds().getValue() * 60));
    }

    /** The minimum delay slider, let go. */
    void minDelayMouseReleased(java.awt.event.MouseEvent evt)
    {
        letGo(evt, () -> this.ui.getModel().getAutoLayout().setMinDelay(this.ui.minDelay().getValue()));
    }

    /**
     * Loads all current settings into the UI
     */
    void loadAutoLayoutSettings()
    {
        org.traincontrol.automation.Layout auto = this.ui.getModel().getAutoLayout();

        this.ui.minDelay().setValue(auto.getMinDelay());
        this.ui.maxDelay().setValue(auto.getMaxDelay());
        this.ui.defaultLocSpeed().setValue(auto.getDefaultLocSpeed());
        this.ui.preArrivalSpeedReduction().setValue(Double.valueOf(auto.getPreArrivalSpeedReduction() * 100).intValue());
        this.ui.maxLocInactiveSeconds().setValue(Double.valueOf(auto.getMaxLocInactiveSeconds() / 60).intValue());
        this.ui.maxActiveTrains().setValue(auto.getMaxActiveTrains());
        this.ui.simulate().setSelected(auto.isSimulate());
        this.ui.atomicRoutes().setSelected(auto.isAtomicRoutes());
        this.ui.turnOffFunctionsOnArrival().setSelected(auto.isTurnOffFunctionsOnArrival());
        this.ui.turnOnFunctionsOnDeparture().setSelected(auto.isTurnOnFunctionsOnDeparture());
        this.ui.maximumLatency().setValue(auto.getMaxLatency());

        // The routing rule, pulled from the layout like every other setting here (MT-226), so the control shows the
        // rule of the configuration that runs.
        if (this.ui.algorithmType() != null)
        {
            this.ui.migrateStoredPathPreference(this.ui.getModel().getAutoLayout());

            // The same restore the refusal uses: the helper holds the flag, so the listener does not run here at all.
            this.ui.restoreRoutingLogicSelection();
        }        
    }
}
