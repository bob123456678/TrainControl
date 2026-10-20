package org.traincontrol.gui;

import java.awt.BorderLayout;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.util.I18n;

/**
 * The autonomy editor's Mass Assign walks: every piece of track and switch with no length, every station with no
 * maximum train length, and every train with no length, each outlined and asked about in turn (FR-136).
 *
 * The panel keeps every method something outside the walks calls, as a one-line call to the method of the same name
 * here, and every field: this reads and writes them through `editor`.  Each dialog is still parented on the editor's
 * window.
 */
final class EditorMassAssign
{
    private final AutonomyEditorPanel editor;

    EditorMassAssign(AutonomyEditorPanel editor)
    {
        this.editor = editor;
    }

    /**
     * Goes through every piece of track on this page that still has no length, and then its switches (Mass Assign
     * Lengths).
     *
     * **Name Everything's walk, for lengths.**  Each piece is outlined whole (it is one run somebody measures with one
     * tape, between two sensors or switches) and scrolled to.  The prompt asks for the WHOLE length of the piece and the
     * session shares it over the piece's squares.  Then every switch on the page that has no length is outlined and one
     * turnout length is asked for, given to each; likewise the squares two roads share.
     *
     * OK, Skip and Cancel as in Name Everything; Escape stops, like Cancel.  0 is an answer - a stretch with no
     * length, kept apart from one nobody has measured (OB-274).  `setupChanged` once at the end, for the running layout;
     * the session itself rebuilds on every write.
     */
    void massAssignLengths()
    {
        // A NEW ROUND OPENS AFRESH; within it, each prompt opens where the last was left (Adam, on MT-454).
        editor.walkPromptAt = null;

        java.util.List<AutonomySession.Stretch> pieces = editor.session.stretchesNeedingALengthOn(editor.page);
        java.util.Set<TileKey> switches = editor.session.switchesNeedingALengthOn(editor.page);
        java.util.Set<TileKey> crossings = editor.session.sharedSquaresNeedingALengthOn(editor.page);

        if (pieces.isEmpty() && switches.isEmpty() && crossings.isEmpty())
        {
            editor.say(editor.hint, I18n.t("autosetup.ui.infoNothingToMeasure"));

            return;
        }

        boolean wroteAny = false;
        boolean stopped = false;

        for (int i = 0; i < pieces.size() && !stopped; i++)
        {
            AutonomySession.Stretch piece = pieces.get(i);

            // Asked again rather than trusted from the list made before the walk began: a walk that trusts a stale list
            // ends up prompting about nothing.
            if (!editor.session.needsALength(piece)) continue;

            editor.outlineAndReveal(piece.getTiles());

            String question = I18n.f("autosetup.ui.promptMassAssignLength", i + 1, pieces.size(),
                nameForPrompt(piece.getFrom()), nameForPrompt(piece.getTo()), piece.getTiles().size());

            Integer whole = askForWholeLength(question, AutonomyEditorPanel.LENGTHS_TITLE);

            // 0 IS AN ANSWER (OB-274: *"allow a length of 0 as a length that is set deliberately ... this will allow
            // everything to get assigned without what appears to be a skip."*).  Skip, and a blank box, leave the piece as
            // it was.
            if (whole == null) stopped = true;
            else if (whole >= 0 && editor.session.assignStretchLength(piece, whole)) wroteAny = true;
        }

        if (!stopped)
        {
            switches = editor.session.switchesNeedingALengthOn(editor.page);

            if (!switches.isEmpty())
            {
                editor.outlineAndReveal(switches);

                String question = I18n.f("autosetup.ui.promptMassAssignSwitches", switches.size());

                Integer turnout = askForWholeLength(question, AutonomyEditorPanel.LENGTHS_TITLE);

                // 0 answers the switches as well (OB-274): two switches back to back are adjacent tracks.
                if (turnout != null && turnout >= 0 && editor.session.assignSwitchLength(switches, turnout)) wroteAny = true;

                if (turnout == null) stopped = true;
            }
        }

        // AND THE SQUARES TWO ROADS SHARE (*"For crossings: if its length is set, count that length once in each
        // direction"*).  Their own step, because a crossing is not a turnout and need not measure like one; one length for
        // all of them, as for switches.
        if (!stopped)
        {
            crossings = editor.session.sharedSquaresNeedingALengthOn(editor.page);

            if (!crossings.isEmpty())
            {
                editor.outlineAndReveal(crossings);

                String question = I18n.f("autosetup.ui.promptMassAssignCrossings", crossings.size());

                Integer across = askForWholeLength(question, AutonomyEditorPanel.LENGTHS_TITLE);

                if (across != null && across >= 0 && editor.session.assignSwitchLength(crossings, across)) wroteAny = true;
            }
        }

        editor.selection.clear();

        if (wroteAny) editor.setupChanged();
        else editor.refresh();
    }

    /**
     * Goes through every station on this page that will still take a train of any length, asking for its maximum (Mass
     * Assign Station Max Train Lengths).
     *
     * **Mass Assign Lengths' walk, for stations**: the same prompt, so the number box has the focus, Enter submits, and
     * each prompt opens where the last was left; OK, Skip and Cancel, with Escape stopping.  Each station is outlined and
     * scrolled to before it is asked about.  0 is refused with a sentence saying why - it means "any length", which is
     * what the station has now - and asked again.  The number lands where the right-click menu's Maximum Train Length
     * puts it.
     */
    void massAssignMaxTrainLengths()
    {
        // A NEW ROUND OPENS AFRESH, as the other two walks.
        editor.walkPromptAt = null;

        java.util.List<TileKey> stations = editor.session.stationsWithoutAMaximumOn(editor.page);

        if (stations.isEmpty())
        {
            editor.say(editor.hint, I18n.t("autosetup.ui.infoEveryStationHasAMaximum"));

            return;
        }

        boolean wroteAny = false;

        for (int i = 0; i < stations.size(); i++)
        {
            TileKey station = stations.get(i);

            // Asked again, as Mass Assign Lengths asks: a list made before the walk began is not trusted.
            if (!editor.session.stationsWithoutAMaximumOn(editor.page).contains(station)) continue;

            editor.outlineAndReveal(java.util.Collections.singleton(station));

            String question = I18n.f("autosetup.ui.promptMassAssignMaxTrainLength", i + 1, stations.size(),
                nameForPrompt(station));

            Integer maximum = askForWholeLength(question, AutonomyEditorPanel.MAXIMA_TITLE);

            while (maximum != null && maximum >= 0 && !editor.session.assignMaxTrainLength(station, maximum))
            {
                JOptionPane.showMessageDialog(editor.owner(), AutonomyEditorPanel.wrapped(I18n.t("autosetup.ui.errorMaxTrainLengthZero")));

                maximum = askForWholeLength(question, AutonomyEditorPanel.MAXIMA_TITLE);
            }

            if (maximum == null) break;

            if (maximum >= 0) wroteAny = true;
        }

        editor.selection.clear();

        // Persisted, and the running layout told - what `promptNumber` does for one station.
        if (wroteAny) editor.setupChanged();
        else editor.refresh();
    }

    /**
     * Goes through every locomotive autonomy would run that has no train length, asking for each one's (Mass Assign
     * Train Lengths, FR-094, named so on MT-567) - and where none is missing one, through every train with the length it
     * has, which Skip keeps (MT-533: the item is never greyed).
     *
     * **Mass Assign Station Max Train Lengths' walk, for trains**: the same prompt, and the list asked again before each
     * train rather than trusted from the start.
     *
     * **Which trains: the list the Atomic Routes refusal names**, asked through `TrainControlUI.trainsWithoutALength`,
     * so the refusal cannot name a train this walk does not offer.
     *
     * **A length is 1 to `TrainControlUI.ROUTE_TRAIN_LENGTH_MAX`**, the range the locomotive menu's own dropdown offers:
     * two places that set one number must not disagree about what a legal value is.  0 is refused with a sentence saying
     * why.  Skip leaves the train as it was.
     *
     * **Written through `applyTrainLength`**, the one door every other train-length gesture uses, so the findings and the
     * grey follow the number as they do from the locomotive menu.  A train length belongs to the locomotive, not to this
     * setup, so the editor's Cancel does not take it back, and each prompt says so before the answer is given.
     *
     * A train standing on this page is outlined and scrolled to while it is asked about; one elsewhere, or standing
     * nowhere, is named and nothing is outlined.
     */
    void massAssignTrainLengths()
    {
        // A NEW ROUND OPENS AFRESH, as the other walks.
        editor.walkPromptAt = null;

        AutonomyEditorPanel.TrainLengthDoor door = editor.trainLengthDoor();

        java.util.List<String> trains = door.trainsWithoutALength();

        // EVERY TRAIN WHEN NONE IS MISSING A LENGTH (MT-533): the item is never greyed, and this is what it offers then -
        // each train with the length it has, which Skip keeps.
        java.util.Map<String, Integer> known = trains.isEmpty() ? door.trainLengths() : null;

        if (known != null) trains = new java.util.ArrayList<>(known.keySet());

        if (trains.isEmpty())
        {
            editor.say(editor.hint, I18n.t("autosetup.ui.infoNoTrainToMeasure"));

            return;
        }

        for (int i = 0; i < trains.size(); i++)
        {
            String train = trains.get(i);

            // Asked again, as the other walks ask: a list made before the walk began is not trusted.  Not when going
            // through every train, where one with a length is the point.
            if (known == null && !door.trainsWithoutALength().contains(train)) continue;

            TileKey standing = squareOfTrain(train);

            if (standing != null && editor.page != null && editor.page.equals(standing.getPage()))
            {
                editor.outlineAndReveal(java.util.Collections.singleton(standing));
            }
            else
            {
                editor.selection.clear();
                editor.refresh();
            }

            String question = known != null
                ? standing == null
                    ? I18n.f("autosetup.ui.promptMassAssignTrainLengthKnownNotPlaced", i + 1, trains.size(), train,
                        known.get(train))
                    : I18n.f("autosetup.ui.promptMassAssignTrainLengthKnown", i + 1, trains.size(), train,
                        nameForPrompt(standing), known.get(train))
                : standing == null
                    ? I18n.f("autosetup.ui.promptMassAssignTrainLengthNotPlaced", i + 1, trains.size(), train)
                    : I18n.f("autosetup.ui.promptMassAssignTrainLength", i + 1, trains.size(), train,
                        nameForPrompt(standing));

            // THE LENGTH IT HAS, in the box (MT-566: *"prefill the textbox with the current length, if any"*), selected so a
            // number typed replaces it.  Only when going through every train: a train missing one has none.
            Integer had = known != null ? known.get(train) : null;

            String prefill = had != null && had > 0 ? String.valueOf(had) : "";

            Integer units = askForWholeLength(question, AutonomyEditorPanel.TRAINS_TITLE, prefill);

            while (units != null && units >= 0 && !AutonomyEditorPanel.acceptsATrainLength(units))
            {
                JOptionPane.showMessageDialog(editor.owner(), AutonomyEditorPanel.wrapped(I18n.f(known != null
                    ? "autosetup.ui.errorTrainLengthOutOfRangeKnown" : "autosetup.ui.errorTrainLengthOutOfRange",
                    TrainControlUI.ROUTE_TRAIN_LENGTH_MAX)));

                units = askForWholeLength(question, AutonomyEditorPanel.TRAINS_TITLE, prefill);
            }

            if (units == null) break;

            if (units > 0) door.applyTrainLength(train, units);
        }

        editor.selection.clear();

        editor.refresh();
    }

    /**
     * @param train a locomotive's name
     * @return the square it stands on in this setup, or null when it stands nowhere
     */
    private TileKey squareOfTrain(String train)
    {
        if (editor.session == null || train == null) return null;

        for (Map.Entry<TileKey, String> placed : editor.session.placementsAutonomyWillWrite().entrySet())
        {
            if (train.equals(placed.getValue())) return placed.getKey();
        }

        return null;
    }

    /**
     * @return the name the setup gives this square, or where it is when it has none
     */
    String nameForPrompt(TileKey tile)
    {
        if (tile == null) return "";

        // A piece ends at a sensor or at a switch.  A switch has no name of its own in the setup, so it is named by
        // where it is (not the sensor a leg led to, where no train need rest).
        LayoutDiagramComponent component = editor.session.getGraph() == null ? null : editor.session.getGraph().getTiles().get(tile);

        if (component != null && component.isSwitch())
        {
            return I18n.f("autosetup.ui.switchAtSquare", tile.getX() + "," + tile.getY());
        }

        // A CROSSING HAS NO NAME EITHER.  A piece now ends at one as well as at a switch, and naming it by
        // its raw key put `main:18,10` in the middle of a sentence.
        if (editor.session.sharedSquaresALengthRuleReads().contains(tile))
        {
            return I18n.f("autosetup.ui.crossingAtSquare", tile.getX() + "," + tile.getY());
        }

        String name = editor.session.getStore().getPointName(tile);

        if (name != null && !name.trim().isEmpty()) return name;

        // AN UNNAMED SENSOR IS NAMED BY WHERE IT IS, as a switch and a crossing already are.  The raw key
        // - the page name, a colon and two numbers - reads as a fault code in a sentence about a stretch of track.
        return I18n.f("autosetup.ui.sensorAtSquare", tile.getX() + "," + tile.getY());
    }

    /**
     * Asks for one number, with Skip beside OK - the Name Everything dialog, for a number.  Both number walks use it: a
     * stretch's whole length, and a station's maximum train length.
     *
     * @param question what to say above the field
     * @param titleKey the bundle key of the walk's name, which is the prompt's title
     * @return the number; -1 for Skip or a blank answer; null for Stop
     */
    private Integer askForWholeLength(String question, String titleKey)
    {
        return askForWholeLength(question, titleKey, "");
    }

    /**
     * The same, the box holding a number to start from (MT-566): the length a train already has, in the walk through
     * every train.  Selected when the box takes the keyboard, so a number typed replaces it rather than being added to
     * it; OK or Enter keeps it, as Skip does.
     *
     * @param question what to say above the field
     * @param titleKey the bundle key of the walk's name, which is the prompt's title
     * @param prefill what the box holds to start with, or empty
     * @return the number; -1 for Skip or a blank answer; null for Stop
     */
    private Integer askForWholeLength(String question, String titleKey, String prefill)
    {
        final javax.swing.JTextField field = editor.digitsOnly(prefill == null ? "" : prefill);

        if (prefill != null && !prefill.isEmpty())
        {
            field.addFocusListener(new java.awt.event.FocusAdapter()
            {
                @Override
                public void focusGained(java.awt.event.FocusEvent e)
                {
                    field.selectAll();
                }
            });
        }

        JPanel panel = new JPanel(new java.awt.BorderLayout(0, 6));

        panel.add(new JLabel(AutonomyEditorPanel.wrapped(question)), java.awt.BorderLayout.NORTH);
        panel.add(field, java.awt.BorderLayout.CENTER);

        final Object[] answers = { I18n.t("ui.ok"), I18n.t("autosetup.ui.btnSkipOne"), I18n.t("ui.cancel") };

        final JOptionPane pane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE,
            // NO INITIAL VALUE, so the field has the keyboard focus (MT-454: *"make sure the entry field is focused"*): with OK as the
            // initial value, JOptionPane gave the focus to that button, so what was typed went nowhere and Enter on the empty field
            // counted as Skip.  Enter in the field still answers OK, through the field's own listener.
            JOptionPane.YES_NO_CANCEL_OPTION, null, answers, null);

        final javax.swing.JDialog dialog = pane.createDialog(editor.owner(), I18n.t(titleKey));

        editor.placeWalkPrompt(dialog);

        field.addActionListener(e ->
        {
            pane.setValue(answers[0]);
            dialog.dispose();
        });

        // AND ASKED FOR BY NAME WHENEVER THE PROMPT HAS THE KEYBOARD (MT-474: *"make sure the text field is focused by default"*):
        // the field takes the focus as the first focusable thing, but that is the focus policy's choice rather than this prompt's,
        // so the prompt says which component it means, when the window opens and when it gains the keyboard back.
        dialog.addWindowListener(new java.awt.event.WindowAdapter()
        {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e)
            {
                field.requestFocusInWindow();
            }
        });

        dialog.addWindowFocusListener(new java.awt.event.WindowAdapter()
        {
            @Override
            public void windowGainedFocus(java.awt.event.WindowEvent e)
            {
                field.requestFocusInWindow();
            }
        });

        dialog.setVisible(true);

        editor.walkPromptAt = dialog.getLocation();

        dialog.dispose();

        int answer = AutonomyEditorPanel.dialogAnswer(pane.getValue(), answers);

        if (answer == AutonomyEditorPanel.ANSWER_STOP) return null;
        if (answer == AutonomyEditorPanel.ANSWER_SKIP) return -1;

        String entered = field.getText().trim();

        if (entered.isEmpty()) return -1;

        try
        {
            return Integer.parseInt(entered);
        }
        catch (NumberFormatException cannotHappen)
        {
            // digitsOnly admits three digits and nothing else, so this is unreachable; a whole piece is at most 999.
            return -1;
        }
    }
}
