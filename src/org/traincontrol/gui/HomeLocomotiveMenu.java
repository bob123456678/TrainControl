package org.traincontrol.gui;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JComponent;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import org.traincontrol.util.I18n;

/**
 * The home locomotive menu items, shared by the three menus that offer them.
 *
 * Two of these menus are station-scoped and carry the same pair of items, and the rules around them
 * are easy to get quietly different in two places: which name is shown, whether an assignment naming a
 * locomotive that is not on the graph survives being looked at, and what has to be refreshed after a
 * change.  One copy, three callers.
 *
 * @author Adam
 */
final class HomeLocomotiveMenu
{
    private HomeLocomotiveMenu()
    {
    }

    /**
     * Adds a submenu holding everything to do with this station having a locomotive of its own.
     *
     * Its title carries the current assignment, so the answer is readable without opening it, and the
     * two actions stay out of menus that are already long.  Nothing is added at all when there is
     * neither an assignment to show nor the possibility of making one - which is every layout that
     * existed before this feature, so those menus are untouched.
     *
     * @param menu
     * @param ui
     * @param p
     * @param dialogParent
     * @param afterChange runs after an assignment changes, for whatever else the caller has to repaint
     */
    static void addStationMenu(JComponent menu, TrainControlUI ui, Point p, Component dialogParent,
        Runnable afterChange)
    {
        Layout layout = ui.getModel().getAutoLayout();

        // Only a station can hold a resting train, so only a station can be given one to hold.  An
        // assignment left behind on something that is no longer a station still has to be reachable -
        // see below - so the submenu appears for either reason.
        boolean canAssign = p.isDestination();
        boolean canClear = p.getHomeLoc() != null;

        if (!canAssign && !canClear) return;

        JMenu home = new JMenu(
            I18n.f(
                "autolayout.ui.menuHomeLocomotive",
                (canClear ? shortName(p.getHomeLoc()) : I18n.t("autolayout.ui.none"))
            )
        );

        home.setToolTipText(I18n.t("autolayout.ui.tooltip.HomeLocomotive"));

        // Says what is assigned, plainly, at the head of the group it belongs to - the collapsed title
        // is the glance, and this is the answer once the submenu is open.
        //
        // Shown in full while the title above is cut short.  The title competes for width with every
        // other item in the parent menu and this one does not, and a tooltip could not make up the
        // difference: Swing does not dispatch mouse events to disabled components, so a tooltip set here
        // would never appear.  Truncating both would lose the long name with nowhere left to read it.
        if (canClear)
        {
            JMenuItem current = new JMenuItem(p.getHomeLoc());

            current.setEnabled(false);

            home.add(current);
            home.addSeparator();
        }

        if (canAssign)
        {
            JMenuItem setItem = new JMenuItem(I18n.t("autolayout.ui.menuSetHomeLocomotive"));

            setItem.addActionListener(event -> chooseHomeLocomotive(ui, p, dialogParent, afterChange));

            // Editing this during a run would do more than it says: applying it re-derives the homes of
            // every unassigned locomotive from where they are standing at that moment, which mid-run is
            // wherever autonomy has happened to leave them.  With nothing on the graph there is also
            // nothing to pick, though an assignment already made stays visible and clearable below.
            if (layout.isRunning())
            {
                setItem.setEnabled(false);
                setItem.setToolTipText(I18n.t("autolayout.ui.errorWaitForActiveLocomotivesToStop"));
            }
            else if (layout.getLocomotivesToRun().isEmpty())
            {
                setItem.setEnabled(false);
                setItem.setToolTipText(I18n.t("autolayout.ui.errorNoLocomotivesOnGraph"));
            }

            home.add(setItem);
        }

        // Offered whether or not this is still a station.  Un-marking one leaves its assignment behind,
        // and a home nothing can ever rest at makes the planner refuse the entire run - so the way out
        // has to stay reachable from the point that caused it.
        if (canClear)
        {
            JMenuItem clearItem = new JMenuItem(I18n.t("autolayout.ui.menuResetHomeLocomotive"));

            clearItem.addActionListener(event -> apply(ui, p.getName(), null, dialogParent, afterChange));
            clearItem.setEnabled(!layout.isRunning());

            if (layout.isRunning())
            {
                clearItem.setToolTipText(I18n.t("autolayout.ui.errorWaitForActiveLocomotivesToStop"));
            }

            home.add(clearItem);
        }

        menu.add(home);
    }

    /**
     * Adds the item that drops every assignment, shown only when there is something to drop.
     *
     * Hidden rather than greyed, because a layout with no assignments is every layout that existed
     * before this feature, and those menus should be untouched.
     *
     * @param menu
     * @param ui
     * @param dialogParent
     * @param afterChange
     */
    static void addClearAllItem(JComponent menu, TrainControlUI ui, Component dialogParent,
        Runnable afterChange)
    {
        Layout layout = ui.getModel().getAutoLayout();

        if (!layout.hasHomeLocomotives()) return;

        JMenuItem menuItem = new JMenuItem(I18n.t("autolayout.ui.menuClearAllHomeLocomotives"));

        menuItem.addActionListener(event ->
        {
            int dialogResult = JOptionPane.showOptionDialog(
                dialogParent,
                I18n.t("autolayout.ui.confirmClearAllHomeLocomotives"),
                I18n.t("autolayout.ui.confirmDeletionTitle"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                TrainControlUI.YES_NO_OPTS,
                TrainControlUI.YES_NO_OPTS[0]
            );

            if (dialogResult == JOptionPane.YES_OPTION)
            {
                if (refuseWhileRunning(ui, dialogParent)) return;

                layout.clearHomeLocomotives();

                afterChange.run();
                ui.repaintAutoLocList(false);
            }
        });

        menuItem.setEnabled(!layout.isRunning());

        if (layout.isRunning())
        {
            menuItem.setToolTipText(I18n.t("autolayout.ui.errorWaitForActiveLocomotivesToStop"));
        }

        menu.add(menuItem);
    }

    /**
     * A locomotive name cut down to what a menu row can carry, as elsewhere in the UI.
     *
     * Only used for the collapsed submenu title, which sits among the other items of a menu that is
     * already wide.  Opening it shows the name in full.
     */
    private static String shortName(String name)
    {
        return name.length() > TrainControlUI.MAX_MENU_LOC_NAME_LENGTH
            ? name.substring(0, TrainControlUI.MAX_MENU_LOC_NAME_LENGTH) + "..."
            : name;
    }

    /**
     * Whether a run started after this menu was built, in which case nothing here may be applied.
     *
     * The greying done when the items are created is a hint about a moment that has already passed - a
     * popup keeps whatever state it was constructed with, and autonomy can start from elsewhere while
     * it sits open.  Applying an assignment mid-run would do more than it says: it re-derives the home
     * of every unassigned locomotive from wherever autonomy has left it standing.
     */
    private static boolean refuseWhileRunning(TrainControlUI ui, Component dialogParent)
    {
        if (!ui.getModel().getAutoLayout().isRunning()) return false;

        JOptionPane.showMessageDialog(
            dialogParent,
            I18n.t("autolayout.ui.errorWaitForActiveLocomotivesToStop")
        );

        return true;
    }

    /**
     * Asks which locomotive belongs at this station, and assigns it.
     */
    private static void chooseHomeLocomotive(TrainControlUI ui, Point p, Component dialogParent,
        Runnable afterChange)
    {
        List<String> names = new ArrayList<>();

        for (Locomotive l : ui.getModel().getAutoLayout().getLocomotivesToRun())
        {
            names.add(l.getName());
        }

        Collections.sort(names);

        // An assignment is allowed to name a locomotive that is not on the graph, and stays that way
        // until it is changed.  Leaving that name out of the list would make the current assignment the
        // one thing that cannot be chosen - opening this dialog and pressing OK would then quietly
        // reassign the station to whatever happened to be listed first.
        if (p.getHomeLoc() != null && !names.contains(p.getHomeLoc()))
        {
            names.add(0, p.getHomeLoc());
        }

        if (names.isEmpty()) return;

        String choice = (String) JOptionPane.showInputDialog(
            dialogParent,
            I18n.f("autolayout.ui.promptChooseHomeLocomotive", p.getName()),
            I18n.t("autolayout.ui.dialogSetHomeLocomotive"),
            JOptionPane.QUESTION_MESSAGE,
            null,
            names.toArray(),
            (p.getHomeLoc() != null ? p.getHomeLoc() : names.get(0))
        );

        if (choice != null)
        {
            apply(ui, p.getName(), choice, dialogParent, afterChange);
        }
    }

    /**
     * Writes the assignment through and repaints whatever showed the old one.
     */
    private static void apply(TrainControlUI ui, String pointName, String locName,
        Component dialogParent, Runnable afterChange)
    {
        if (refuseWhileRunning(ui, dialogParent)) return;

        try
        {
            ui.getModel().getAutoLayout().setHomeLocomotive(pointName, locName);

            afterChange.run();

            // The home markers in the locomotive list, and whether returning home has anything to do
            ui.repaintAutoLocList(false);
        }
        catch (Exception e)
        {
            JOptionPane.showMessageDialog(
                dialogParent,
                I18n.f("autolayout.ui.errorSetHomeLocomotive", e.getMessage())
            );
        }
    }
}
