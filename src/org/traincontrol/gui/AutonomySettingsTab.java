package org.traincontrol.gui;

import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.traincontrol.automation.Layout;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.Route;
import org.traincontrol.util.I18n;

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
    void autoRouteListMouseReleased(MouseEvent evt)
    {
        if (TrainControlUI.onAGreyedControl(evt)) return;

        SwingUtilities.invokeLater(() ->
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
                        this.ui.routeListPanel.refreshRouteList();
                    }
                }
            });
    }

    /**
     * Activate Routes, let go: switches arming the picked routes on or off, unless a run holds the setup.
     */
    void toggleSpecifiedRoutesMouseReleased(MouseEvent evt)
    {
        if (TrainControlUI.onAGreyedControl(evt)) return;

        SwingUtilities.invokeLater(() ->
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
    private void letGo(MouseEvent evt, Setting setting)
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
    void maxActiveTrainsMouseReleased(MouseEvent evt)
    {
        letGo(evt, () -> this.ui.getModel().getAutoLayout().setMaxActiveTrains(this.ui.maxActiveTrains().getValue()));
    }

    /** The maximum latency slider, let go. */
    void maximumLatencyMouseReleased(MouseEvent evt)
    {
        letGo(evt, () ->
        {
            this.ui.getModel().getAutoLayout().setMaxLatency(this.ui.maximumLatency().getValue());
            this.ui.maximumLatency().setValue(this.ui.getModel().getAutoLayout().getMaxLatency());
        });
    }

    /** The functions-on-departure box, let go. */
    void turnOnFunctionsOnDepartureMouseReleased(MouseEvent evt)
    {
        letGo(evt, () -> this.ui.getModel().getAutoLayout()
            .setTurnOnFunctionsOnDeparture(this.ui.turnOnFunctionsOnDeparture().isSelected()));
    }

    /** The simulate box, let go. */
    void simulateMouseReleased(MouseEvent evt)
    {
        letGo(evt, () -> this.ui.getModel().getAutoLayout().setSimulate(this.ui.simulate().isSelected()));
    }

    /** The functions-off-on-arrival box, let go. */
    void turnOffFunctionsOnArrivalMouseReleased(MouseEvent evt)
    {
        letGo(evt, () -> this.ui.getModel().getAutoLayout()
            .setTurnOffFunctionsOnArrival(this.ui.turnOffFunctionsOnArrival().isSelected()));
    }

    /**
     * The atomic routes box, let go: switching it off is refused while the railway lacks the lengths that needs.
     */
    void atomicRoutesMouseReleased(MouseEvent evt)
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
    void preArrivalSpeedReductionMouseReleased(MouseEvent evt)
    {
        letGo(evt, () -> this.ui.getModel().getAutoLayout()
            .setPreArrivalSpeedReduction(Double.valueOf(this.ui.preArrivalSpeedReduction().getValue()) / 100));
    }

    /** The default speed slider, let go. */
    void defaultLocSpeedMouseReleased(MouseEvent evt)
    {
        letGo(evt, () -> this.ui.getModel().getAutoLayout().setDefaultLocSpeed(this.ui.defaultLocSpeed().getValue()));
    }

    /** The maximum delay slider, let go. */
    void maxDelayMouseReleased(MouseEvent evt)
    {
        letGo(evt, () -> this.ui.getModel().getAutoLayout().setMaxDelay(this.ui.maxDelay().getValue()));
    }

    /** The inactive-minutes slider, let go. */
    void maxLocInactiveSecondsMouseReleased(MouseEvent evt)
    {
        letGo(evt, () -> this.ui.getModel().getAutoLayout()
            .setMaxLocInactiveSeconds(this.ui.maxLocInactiveSeconds().getValue() * 60));
    }

    /** The minimum delay slider, let go. */
    void minDelayMouseReleased(MouseEvent evt)
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
        this.ui.preArrivalSpeedReduction().setValue((int) Math.round(auto.getPreArrivalSpeedReduction() * 100));
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
            migrateStoredPathPreference(this.ui.getModel().getAutoLayout());

            // The same restore the refusal uses: the helper holds the flag, so the listener does not run here at all.
            restoreRoutingLogicSelection();
        }        
    }

    /** Puts what the chosen routing rule does onto its control, so ten similar options can be told apart (OB-163). */
    void refreshRoutingLogicTooltip()
    {
        if (this.ui.algorithmType() == null) return;

        String general = I18n.t("ui.main.tooltip.routingLogic");

        int at = this.ui.algorithmType().getSelectedIndex();

        if (at < 0 || at >= TrainControlUI.ROUTING_ORDER.length)
        {
            TrainControlUI.setToolTipAtRest(this.ui.algorithmType(), general);

            return;
        }

        // HTML so the two paragraphs wrap.
        TrainControlUI.setToolTipAtRest(this.ui.algorithmType(), "<html><p width=\"420\">" + general + "<br><br>"
            + I18n.t("autolayout.ui.tooltip.pathPreference" + TrainControlUI.ROUTING_ORDER[at].name())
            + "</p></html>");
    }

    /** Puts the dropdown back to the rule in force with the flag held, so the listener stays quiet. */
    void restoreRoutingLogicSelection()
    {
        if (this.ui.algorithmType() == null || this.ui.getModel() == null || !this.ui.getModel().hasAutoLayout()) return;

        Layout.PathPreference now = this.ui.getModel().getAutoLayout().getPathPreference();

        this.ui.restoringRoutingLogic = true;

        try
        {
            for (int at = 0; at < TrainControlUI.ROUTING_ORDER.length; at++)
            {
                if (TrainControlUI.ROUTING_ORDER[at] == now)
                {
                    this.ui.algorithmType().setSelectedIndex(at);

                    // Inside the flag the listener is suppressed, so ask for the tooltip here too.
                    refreshRoutingLogicTooltip();

                    return;
                }
            }
        }
        finally
        {
            this.ui.restoringRoutingLogic = false;
        }
    }

    /** Fills in the routing dropdown (MT-226) without touching its layout: a GroupLayout.replace can stop the start. */
    void mountRoutingLogic()
    {
        final JComboBox<String> algorithmType = this.ui.algorithmType();

        if (algorithmType == null) return;

        // Translated at runtime: the form's English label is not ours to edit.
        if (this.ui.jLabel2() != null)
        {
            this.ui.jLabel2().setText(I18n.t("ui.main.routingLogic"));
            this.ui.jLabel2().setToolTipText(I18n.t("ui.main.tooltip.routingLogic"));
        }

        algorithmType.removeAllItems();

        for (Layout.PathPreference option : TrainControlUI.ROUTING_ORDER)
        {
            algorithmType.addItem(I18n.t("autolayout.ui.pathPreference" + option.name()));
        }

        // Capped at 230 px, the editor's alignment width.
        Dimension capped = new Dimension(230, algorithmType.getPreferredSize().height);

        algorithmType.setPreferredSize(capped);
        algorithmType.setMaximumSize(capped);

        refreshRoutingLogicTooltip();

        algorithmType.addActionListener(event ->
        {
            // Our own restore, not a person; checked here because setSelectedIndex delivers this synchronously.
            if (this.ui.restoringRoutingLogic) return;

            if (this.ui.getModel() == null || !this.ui.getModel().hasAutoLayout()
                || this.ui.getModel().getAutoLayout() == null) return;

            SwingUtilities.invokeLater(() ->
            {
                if (this.ui.getModel().hasAutoLayout())
                {
                    // Back before the modal message: a rule changed mid-run would leave two in force.
                    if (!this.ui.refuseWhileARunHoldsTheSetup(this.ui, () ->
                        {
                            this.ui.applyAutoRouteListSelections();
                            restoreRoutingLogicSelection();
                        }))
                    {
                        int at = algorithmType.getSelectedIndex();

                        if (at < 0 || at >= TrainControlUI.ROUTING_ORDER.length) return;

                        Layout.PathPreference option = TrainControlUI.ROUTING_ORDER[at];

                        // Nothing to do, and saying so would write the setup on every refresh.
                        if (this.ui.getModel().getAutoLayout().getPathPreference() == option) return;

                        this.ui.getModel().getAutoLayout().setPathPreference(option);

                        refreshRoutingLogicTooltip();

                        // Said aloud when it cannot be kept; true: a person just chose this.
                        if (!persistPathPreference(option, true))
                        {
                            this.ui.getModel().logf(java.util.logging.Level.WARNING, "autolayout.warnRoutingRuleNotStored",
                                I18n.t("autolayout.ui.pathPreference" + option.name()));
                        }
                    }
                }
            });
        });
    }

    /** Writes the routing rule into the configuration; true when stored.  `mayBuildSession` only after a click. */
    private boolean persistPathPreference(Layout.PathPreference option, boolean mayBuildSession)
    {
        try
        {
            // A refresh must not run the lazy session builder.
            AutonomySession session = mayBuildSession ? this.ui.getAutonomySession() : this.ui.autonomySession();

            return session != null && session.setGlobal("pathPreference", option.name());
        }
        catch (Exception e)
        {
            if (this.ui.getModel() != null) this.ui.getModel().log(e);

            return false;
        }
    }

    /** Carries the pre-configuration routing choice across once, if the configuration has none; `live` may be null. */
    void migrateStoredPathPreference(Layout live)
    {
        if (live == null) return;

        final String legacy = "AutonomyPathPreference";

        String was = TrainControlUI.getPrefs().get(legacy, null);

        if (was == null) return;

        // A second call cannot tell the user's own rule from the one just written.
        if (this.ui.pathPreferenceCarried) return;

        // The field, not getAutonomySession(): this runs from a refresh.
        AutonomySession stored = this.ui.autonomySession();

        // A legacy autonomy.json can carry a rule getGlobal cannot see.
        if (live.getPathPreference() != Layout.PathPreference.RANDOM)
        {
            TrainControlUI.getPrefs().remove(legacy);

            return;
        }

        if (stored != null && stored.getGlobal("pathPreference") != null)
        {
            // The configuration has its own answer: the old one is stale.
            TrainControlUI.getPrefs().remove(legacy);

            return;
        }

        try
        {
            Layout.PathPreference option = Layout.PathPreference.valueOf(was);

            live.setPathPreference(option);

            this.ui.pathPreferenceCarried = true;

            // The old key goes only once the new home has it.
            if (persistPathPreference(option, false)) TrainControlUI.getPrefs().remove(legacy);
        }
        catch (Exception e)
        {
            // The key stays so it can be retried.
            if (this.ui.getModel() != null) this.ui.getModel().log(e);
        }
    }
}
