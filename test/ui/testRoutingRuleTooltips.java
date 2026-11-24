package ui;

import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout.PathPreference;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;
import support.Display;
import support.Reflect;
import static support.SandboxedWindow.build;

/**
 * Every routing rule explains itself on the control that chooses it.
 *
 * Each rule has an explanation written for it and translated into all eight languages
 * (`autolayout.ui.tooltip.pathPreferenceFEWEST_STATIONS` and its siblings).  A rule whose text nobody can see is a rule
 * whose missing text nobody notices, hence this asks the CONTROL, not the properties file.
 *
 * MUTATION this catches: reverting the tooltip to the general sentence fails every rule; adding a
 * rule to ROUTING_ORDER without writing its explanation fails that rule.
 *
 * @author Adam
 */
public class testRoutingRuleTooltips
{
    @Test
    public void testEachRuleSaysWhatItDoes() throws Exception
    {
        Display.needed("the dropdown is a control in a window");

        TrainControlUI ui = build();

        javax.swing.JComboBox<?> dropdown = dropdownOf(ui);

        PathPreference[] rules = (PathPreference[]) Reflect.get(TrainControlUI.class, null, "ROUTING_ORDER");

        assertEquals(dropdown.getItemCount(), rules.length,
            "the dropdown does not offer one item per rule, so the indexes below name the wrong ones");

        java.lang.reflect.Method refresh = Reflect.method(TrainControlUI.class, "refreshRoutingLogicTooltip");

        String general = I18n.t("ui.main.tooltip.routingLogic");

        for (int at = 0; at < rules.length; at++)
        {
            String key = "autolayout.ui.tooltip.pathPreference" + rules[at].name();

            String own;

            // I18n.t THROWS on a missing key rather than handing the key back, so a rule added
            // without an explanation would come out of here as a MissingResourceException with no
            // hint of which rule or why.  Caught and named.
            try
            {
                own = I18n.t(key);
            }
            catch (java.util.MissingResourceException missing)
            {
                fail(rules[at] + " has no explanation written for it: " + key + " is not in the bundle");

                return;
            }

            assertFalse(own.trim().isEmpty(), rules[at] + " has an empty explanation");

            dropdown.setSelectedIndex(at);

            refresh.invoke(ui);

            String tip = dropdown.getToolTipText();

            assertNotNull(tip, rules[at] + " leaves the control with no tooltip at all");

            assertTrue(tip.contains(own),
                rules[at] + " is chosen and the control does not say what it does.  Tooltip: " + tip);

            assertTrue(tip.contains(general),
                rules[at] + " lost the general explanation of what the control is for");
        }
    }

    /** With no rule chosen the control says only what it is for, and every rule the layout can hold is offered. */
    @Test
    public void testNoRuleChosenSaysOnlyWhatTheControlIsFor() throws Exception
    {
        Display.needed("the dropdown is a control in a window");

        TrainControlUI ui = build();

        PathPreference[] rules = (PathPreference[]) Reflect.get(TrainControlUI.class, null, "ROUTING_ORDER");

        assertEquals(java.util.EnumSet.copyOf(java.util.Arrays.asList(rules)),
            java.util.EnumSet.allOf(PathPreference.class), "a rule of the layout is not offered, or one is offered twice");

        assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(rules)).size(), rules.length, "a rule is offered twice");

        javax.swing.JComboBox<?> dropdown = dropdownOf(ui);

        dropdown.setSelectedIndex(-1);

        Reflect.method(TrainControlUI.class, "refreshRoutingLogicTooltip").invoke(ui);

        assertEquals(dropdown.getToolTipText(), I18n.t("ui.main.tooltip.routingLogic"),
            "with no rule chosen the control said more than what it is for");
    }

    /**
     * The routing dropdown, which is a generated field and so has no accessor.
     */
    private javax.swing.JComboBox<?> dropdownOf(TrainControlUI ui) throws Exception
    {
        return (javax.swing.JComboBox<?>) Reflect.get(TrainControlUI.class, ui, "algorithmType");
    }
}
