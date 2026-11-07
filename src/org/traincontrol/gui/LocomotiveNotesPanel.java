package org.traincontrol.gui;

import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.LocomotiveNotes;
import org.traincontrol.util.I18n;

/**
 * The locomotive's years, railway and notes as one panel: the Edit Notes dialog and the Notes tab of the Locomotive
 * Settings window both show it and both write through {@link #apply}.
 */
final class LocomotiveNotesPanel extends JPanel
{
    private final JTextField startYear;
    private final JTextField endYear;
    private final JTextField railway;
    private final JTextArea notes;

    private String loadedStart;
    private String loadedEnd;
    private String loadedRailway;
    private String loadedNotes;

    /**
     * @param l the locomotive whose notes are shown
     * @param textRows the height of the notes box
     * @param textColumns the width of the notes box
     * @param onChange told whenever anything is typed, or null
     */
    LocomotiveNotesPanel(Locomotive l, int textRows, int textColumns, Runnable onChange)
    {
        super(new GridBagLayout());

        this.startYear = new JTextField(10);
        this.endYear = new JTextField(10);
        this.railway = new JTextField(30);
        this.notes = new JTextArea(textRows, textColumns);

        startYear.setToolTipText(I18n.t("loc.ui.tooltip.StartYear"));
        endYear.setToolTipText(I18n.t("loc.ui.tooltip.EndYear"));
        startYear.setName("notes.startYear");
        endYear.setName("notes.endYear");
        railway.setName("notes.railway");
        notes.setName("notes.text");

        startYear.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyReleased(KeyEvent e)
            {
                TrainControlUI.validateInt(e, false);
                TrainControlUI.limitLength(e, 4);
            }
        });

        endYear.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyReleased(KeyEvent e)
            {
                TrainControlUI.validateInt(e, false);
                TrainControlUI.limitLength(e, 4);
            }
        });

        railway.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyReleased(KeyEvent e)
            {
                TrainControlUI.limitLength(e, TrainControlUI.MAX_LOC_RAILWAY_NAME);
            }
        });

        notes.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));

        reload(l);

        if (onChange != null)
        {
            javax.swing.event.DocumentListener tell = Typing.tell(onChange);

            startYear.getDocument().addDocumentListener(tell);
            endYear.getDocument().addDocumentListener(tell);
            railway.getDocument().addDocumentListener(tell);
            notes.getDocument().addDocumentListener(tell);
        }

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        add(new JLabel(I18n.t("loc.ui.labelStartYear")), gbc);
        gbc.gridx = 1;
        add(startYear, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        add(new JLabel(I18n.t("loc.ui.labelEndYear")), gbc);
        gbc.gridx = 1;
        add(endYear, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        add(new JLabel(I18n.t("loc.ui.labelRailwayName")), gbc);
        gbc.gridx = 1;
        add(railway, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        add(new JLabel(I18n.t("loc.ui.labelNotes")), gbc);

        gbc.gridy++;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        add(new JScrollPane(notes), gbc);
    }

    /** Shows what the locomotive holds now, and makes that what {@link #isDirty} compares against. */
    void reload(Locomotive l)
    {
        LocomotiveNotes existing = l.getStructuredNotes();

        loadedStart = existing.getStartYear() == 0 ? "" : String.valueOf(existing.getStartYear());
        loadedEnd = existing.getEndYear() == 0 ? "" : String.valueOf(existing.getEndYear());
        loadedRailway = existing.getRailway() == null ? "" : existing.getRailway();
        loadedNotes = existing.getNotes() == null ? "" : existing.getNotes();

        startYear.setText(loadedStart);
        endYear.setText(loadedEnd);
        railway.setText(loadedRailway);
        notes.setText(loadedNotes);
        notes.setCaretPosition(0);
    }

    /** Whether anything differs from what {@link #reload} showed. */
    boolean isDirty()
    {
        return !startYear.getText().equals(loadedStart) || !endYear.getText().equals(loadedEnd)
            || !railway.getText().equals(loadedRailway) || !notes.getText().equals(loadedNotes);
    }

    /** Gives the notes box the keyboard, for the dialog. */
    void focusNotes()
    {
        notes.requestFocusInWindow();
    }

    /**
     * The two years as numbers, zero for a blank one.
     *
     * @throws NumberFormatException when one is not a whole number
     */
    int[] checkYears()
    {
        String startText = startYear.getText().trim();
        String endText = endYear.getText().trim();

        return new int[] { startText.isEmpty() ? 0 : Math.abs(Integer.parseInt(startText)),
            endText.isEmpty() ? 0 : Math.abs(Integer.parseInt(endText)) };
    }

    /**
     * Writes what is shown onto the locomotive.
     *
     * @throws NumberFormatException when a year is not a whole number; nothing is written then
     */
    void apply(Locomotive l)
    {
        int[] years = checkYears();

        int start = years[0];
        int end = years[1];

        String trimmed = railway.getText().trim();
        String rail = trimmed.substring(0, Math.min(trimmed.length(), TrainControlUI.MAX_LOC_RAILWAY_NAME));
        String text = notes.getText().substring(0, Math.min(notes.getText().length(), TrainControlUI.MAX_LOC_NOTES_LENGTH));

        l.setStructuredNotes(start, end, rail, text);
    }
}
