package ui;

import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout.PathPreference;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;

/**
 * Every routing rule explains itself on the control that chooses it.
 *
 * Each rule has an explanation written for it and translated into all eight languages -
 * `autolayout.ui.tooltip.pathPreferenceFEWEST_STATIONS` and its siblings. Until OB-163 nothing read a
 * single one of them: the dropdown was built from the NAMES and carried only the general sentence
 * about what the control is for, so seventy-two written sentences were unreachable on the one control
 * where ten similarly-worded options have to be told apart.
 *
 * That is also how "Completely at Random" came to be the only rule with no explanation written at all.
 * A rule whose text nobody can see is a rule whose missing text nobody notices, which is the argument
 * for this test rather than for a bundle-only one: it asks the CONTROL, not the properties file.
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
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("the dropdown is a control in a window");
        }

        TrainControlUI ui = build();

        javax.swing.JComboBox<?> dropdown = dropdownOf(ui);

        java.lang.reflect.Field order = TrainControlUI.class.getDeclaredField("ROUTING_ORDER");
        order.setAccessible(true);

        PathPreference[] rules = (PathPreference[]) order.get(null);

        assertEquals(dropdown.getItemCount(), rules.length,
            "the dropdown does not offer one item per rule, so the indexes below name the wrong ones");

        java.lang.reflect.Method refresh =
            TrainControlUI.class.getDeclaredMethod("refreshRoutingLogicTooltip");
        refresh.setAccessible(true);

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

    /**
     * The window, with the layout preference pointed somewhere that is not Adam's railway (OB-111).
     */
    private TrainControlUI build() throws Exception
    {
        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] built = new TrainControlUI[1];

        try
        {
            // Inside the try, so nothing between the open and the close can leave the
            // preference behind (TSX-B8).
            sandbox = support.LayoutSandbox.open();

            javax.swing.SwingUtilities.invokeAndWait(() -> built[0] = new TrainControlUI());
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }

        return built[0];
    }

    /**
     * The routing dropdown, which is a generated field and so has no accessor.
     */
    private javax.swing.JComboBox<?> dropdownOf(TrainControlUI ui) throws Exception
    {
        java.lang.reflect.Field field = TrainControlUI.class.getDeclaredField("algorithmType");

        field.setAccessible(true);

        return (javax.swing.JComboBox<?>) field.get(ui);
    }
}
