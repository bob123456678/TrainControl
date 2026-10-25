package org.traincontrol.gui;

import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.table.DefaultTableModel;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.TimetablePath;
import org.traincontrol.util.Conversion;
import org.traincontrol.util.I18n;

/**
 * The Timetable tab: repainting the table from the railway, how an entry's stations are named in it, and the entry
 * menu's doors - delete an entry, change its delay, restart the timetable and clear it - each refused while a run
 * holds the setup.
 *
 * The table and every field stay on `TrainControlUI`.
 */
final class TimetableTab
{
    private final TrainControlUI ui;

    TimetableTab(TrainControlUI ui)
    {
        this.ui = ui;
    }

    /**
     * Where a timetable entry starts or ends, as the timetable shows it: by the name it was written with where the railway cannot run it,
     * so an entry through a page left out of autonomy is listed like any other.
     */
    String timetableStop(TimetablePath path, boolean start)
    {
        if (path.isRunnable()) return this.ui.stationLabel(start ? path.getStart() : path.getEnd());

        String name = start ? path.getStartName() : path.getEndName();

        org.traincontrol.automationui.AutonomySession session = this.ui.autonomySession();

        String base = session == null || name == null ? null : session.baseNameOf(name);

        return base == null ? String.valueOf(name) : base;
    }

    void repaintTimetable()
    {
        // The snapshot must not be taken on the EDT: getTimetableSnapshot holds the Layout monitor, and configureAndLockPath holds that same
        // monitor across its per-command sleeps (half a second to two seconds on an ordinary path, deliberately, because locking a path has to
        // be atomic).  This method runs at every path start and end, so with two or more trains one locomotive is regularly mid-configuration
        // when another's callback lands here, and taken inside the invokeLater the whole UI froze for the length of someone else's
        // switch-throwing.  The callback thread pays that wait instead, where contention for this monitor is already the design (it holds
        // activeLocomotives, and activeLocomotives -> Layout is the order the completion block establishes); idle callers take it uncontended
        // and hand the EDT a finished copy.  The UI callers' busy guard does not make the monitor free by the time they run: three hold a modal
        // dialog open between the check and the snapshot, and a dispatch started meanwhile takes the monitor (`isRunning()` is true from the
        // moment `Layout.executePath` counts the train's thread, but the guard is asked before that).  Bouncing off the EDT costs one
        // short-lived thread on a user action and asks nothing of the callers, which still marshal their own work through the invokeLater below.
        if (javax.swing.SwingUtilities.isEventDispatchThread())
        {
            new Thread(() -> this.repaintTimetable()).start();
            return;
        }

        // ASKED, NOT BUILT: every milestone's refresh runs this, and after Unload it built an empty railway
        final Layout shown = this.ui.getModel().getAutoLayoutIfLoaded();

        final List<TimetablePath> timeTable =
            shown == null ? java.util.Collections.<TimetablePath>emptyList() : shown.getTimetableSnapshot();

        javax.swing.SwingUtilities.invokeLater(() ->
        {
            this.ui.prepareTimetableColumns();

            // Update the data
            this.ui.timetableCapture().setSelected(shown != null && shown.isTimetableCapture());
            
            // Do nothing if what the table SHOWS has not changed (MT-149): this compared `timeTable.hashCode()`, and a locomotive hashes by
            // IDENTITY (deliberately, so renaming one cannot move it out of the consists and run lists that hold it), so a rename changed the name
            // in every row and no hash at all, and the table went on showing a locomotive that no longer exists under that name (Adam: "the
            // timetable is not updated").  Keyed on the text of the rows instead, the only thing that can answer "does this need redrawing"; one
            // string per repaint of a list tens of entries long.
            String showing = this.ui.timetableSignature(timeTable);

            if (showing.equals(this.ui.lastTimetableState)) return;

            this.ui.lastTimetableState = showing;
            
            DefaultTableModel tableModel = (DefaultTableModel) this.ui.timetable().getModel();
            tableModel.setRowCount(0);
                        
            for (TimetablePath path : timeTable)
            {
                Object[] data = {tableModel.getRowCount() + 1, path.getLoc().getName(),
                timetableStop(path, true), timetableStop(path, false),
                path.isExecuted() ? Conversion.convertSecondsToDatetime(path.getExecutionTime()) : "Pending Start +" + (path.getSecondsToNext() / 1000) + "s"};

                tableModel.addRow(data);
            }
        });
    }

    void clearTimetable()
    {
        javax.swing.SwingUtilities.invokeLater(() -> 
        {
            if (this.ui.refuseWhileARunHoldsTheSetup(this.ui)) return;

            int dialogResult = JOptionPane.showOptionDialog(
                this.ui,
                I18n.t("timetable.ui.confirmRemoveAllEntries"),
                I18n.t("ui.dialogConfirmDeletion"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                TrainControlUI.YES_NO_OPTS,
                // Default to No - this clears the whole timetable, not one entry.
                TrainControlUI.YES_NO_OPTS[1]
            );

            // Anything but Yes.  This one wipes the whole captured timetable with no undo, and
            // Escape - which means no - was reaching it.
            if (dialogResult != JOptionPane.YES_OPTION) return;

            // ASKED AGAIN AFTER THE QUESTION, as Delete Entry asks (OB-260): it can stay open as long as the
            // operator likes, and a run may have started meanwhile
            if (this.ui.refuseWhileARunHoldsTheSetup(this.ui)) return;

            final org.traincontrol.automation.Layout layout = this.ui.getModel().getAutoLayout();

            // UNDER THE RAILWAY'S LOCK, which capture appends under from locomotive threads - so off this thread, as every
            // door onto that lock from the window is (OB-192)
            new Thread(() ->
            {
                layout.emptyTimetable();

                javax.swing.SwingUtilities.invokeLater(() ->
                {
                    // AND THE TIMETABLE THE CONFIGURATION KEEPS, at once: left to the next fold, a Clear pressed
                    // while a page was out came back with the page
                    org.traincontrol.automationui.AutonomySession keeping = this.ui.autonomySession();

                    if (keeping != null && this.ui.getActiveDiagramConfiguration() != null)
                    {
                        try
                        {
                            keeping.clearTheTimetable();
                        }
                        catch (java.io.IOException e)
                        {
                            this.ui.getModel().log(e);
                        }
                    }

                    this.repaintTimetable();
                    this.ui.repaintAutoLocListLite();
                });
            }, "Timetable clear").start();
        });
    }

    void deleteTimetableEntry(MouseEvent evt)
    {
        try
        {
            if (this.ui.refuseWhileARunHoldsTheSetup(this.ui)) return;
            
            int index = this.ui.getTimetableIndexAtCursor(evt);
            
            if (index >= 0)
            {
                // THE ENTRY SHOWN, so the one removed is it or none (OB-260)
                final Object shown = this.ui.getTimetableEntryAtCursor(evt);

                int dialogResult = JOptionPane.showOptionDialog(
                    this.ui,
                    I18n.f(
                        "timetable.ui.confirmRemoveEntryContinue",
                        index,
                        shown
                    ),
                    I18n.t("timetable.ui.dialogConfirmDeletion"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    TrainControlUI.YES_NO_OPTS,
                    // Index 0 is Yes - this said "No" and pre-selected the delete. Enter on
                    // this confirmation deleted the timetable entry with no undo, one keypress rather
                    // than the two the comment believed it was requiring.
                    TrainControlUI.YES_NO_OPTS[1] // default selection = "No"
                );

                // Anything but Yes.
                if (dialogResult != JOptionPane.YES_OPTION) return;

                // ASKED AGAIN AFTER THE QUESTION (OB-260): it can stay open as long as the operator likes, and a run may
                // have started meanwhile
                if (this.ui.refuseWhileARunHoldsTheSetup(this.ui)) return;

                if (!(shown instanceof TimetablePath)) return;

                final org.traincontrol.automation.Layout layout = this.ui.getModel().getAutoLayout();
                final int row = index;

                // UNDER THE RAILWAY'S LOCK, which capture appends under from locomotive threads - so off this thread, as
                // every door onto that lock from the window is (OB-192)
                new Thread(() ->
                {
                    layout.removeTimetableEntry(row, (TimetablePath) shown);

                    javax.swing.SwingUtilities.invokeLater(this::repaintTimetable);
                }, "Timetable entry removal").start();
            }
        }
        catch (Exception e)
        {
            this.ui.getModel().log(e);

            JOptionPane.showMessageDialog(this.ui, I18n.f("error.generic", e.getMessage()));
        }    
    }

    void updateTimetableDelay(MouseEvent evt)
    {
        try
        {
            if (this.ui.refuseWhileARunHoldsTheSetup(this.ui)) return;
            
            TimetablePath ttp = (TimetablePath) this.ui.getTimetableEntryAtCursor(evt);

            if (ttp != null)
            {
                String input = JOptionPane.showInputDialog(
                    this.ui,
                    I18n.t("timetable.ui.enterDelaySecondsBeforeRouteExecutes"),
                    (int) (ttp.getSecondsToNext() / 1000.0)
                );

                if (input != null)
                {
                    // ASKED AGAIN AFTER THE QUESTION, as the other timetable doors ask: it can stay open as
                    // long as the operator likes, and a run started meanwhile may be waiting on this entry's delay
                    if (this.ui.refuseWhileARunHoldsTheSetup(this.ui)) return;

                    ttp.setSecondsToNext(Long.parseLong(input) * 1000);
                    this.repaintTimetable();
                } 
            }
        }
        catch (Exception e)
        {
            this.ui.getModel().log(e);

            JOptionPane.showMessageDialog(this.ui, I18n.f("error.generic", e.getMessage()));
        }
    }

    /**
     * Restarts timetable execution from the top
     */
    void restartTimetable()
    {
        javax.swing.SwingUtilities.invokeLater(() -> 
        {
            if (this.ui.refuseWhileARunHoldsTheSetup(this.ui)) return;
            
            // Check if a reset will do anything
            if (this.ui.getModel().getAutoLayout().getUnfinishedTimetablePathIndex() == 0
                && !this.ui.getModel().getAutoLayout().getTimetable().isEmpty()
                && this.ui.getModel().getAutoLayout().getTimetable().get(0).getExecutionTime() == 0
            )
            {
                JOptionPane.showMessageDialog(
                    this.ui,
                    I18n.t("timetable.ui.infoTimetableAlreadyReset")
                );
                return;
            }

            int dialogResult = JOptionPane.showOptionDialog(
                this.ui,
                I18n.t("timetable.ui.confirmRestartMarkEntriesUnvisited"),
                I18n.t("timetable.ui.dialogConfirmRestart"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                TrainControlUI.YES_NO_OPTS,
                TrainControlUI.YES_NO_OPTS[0]
            );

            // Anything but Yes.
            if (dialogResult != JOptionPane.YES_OPTION) return;

            // ASKED AGAIN AFTER THE QUESTION: a reset under a timetable started meanwhile would set back the
            // execution times its loop waits on
            if (this.ui.refuseWhileARunHoldsTheSetup(this.ui)) return;

            this.ui.getModel().getAutoLayout().resetTimetable();
            this.repaintTimetable();
        });
    }
}
