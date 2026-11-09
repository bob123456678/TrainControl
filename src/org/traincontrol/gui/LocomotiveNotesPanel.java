package org.traincontrol.gui;

import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.LocomotiveNotes;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.util.I18n;

/**
 * The locomotive's years, railway and notes as one panel: the Edit Notes dialog and the Notes tab of the Locomotive
 * Settings window both show it and both write through {@link #apply}.
 */
final class LocomotiveNotesPanel extends JPanel
{
    private final JTextField startYear;
    private final JTextField endYear;
    private final JComboBox<String> railway;
    private final JTextField railwayText;
    private final JTextArea notes;

    private String loadedStart;
    private String loadedEnd;
    private String loadedRailway;
    private String loadedNotes;
    private final java.util.List<JLabel> captions = new java.util.ArrayList<>();
    private JLabel metadataCaption;
    private JLabel notesCaption;

    /**
     * @param l the locomotive whose notes are shown
     * @param railways the railway names to offer, as {@link #railwayNames} lists them
     * @param textRows the height of the notes box
     * @param textColumns the width of the notes box
     * @param onChange told whenever anything is typed, or null
     */
    LocomotiveNotesPanel(Locomotive l, java.util.List<String> railways, int textRows, int textColumns, Runnable onChange)
    {
        super(new GridBagLayout());

        this.startYear = yearField();
        this.endYear = yearField();
        this.railway = new JComboBox<>(railways.toArray(new String[0]));
        this.railway.setEditable(true);
        this.railwayText = (JTextField) railway.getEditor().getEditorComponent();
        this.notes = new JTextArea(textRows, textColumns);

        startYear.setToolTipText(I18n.t("loc.ui.tooltip.StartYear"));
        endYear.setToolTipText(I18n.t("loc.ui.tooltip.EndYear"));
        startYear.setName("notes.startYear");
        endYear.setName("notes.endYear");
        railway.setName("notes.railway");
        railwayText.setFont(startYear.getFont());
        railway.setFont(startYear.getFont());
        railway.setPreferredSize(new java.awt.Dimension(startYear.getPreferredSize().width * 3,
            startYear.getPreferredSize().height + 2));
        notes.setName("notes.text");

        railwayText.addKeyListener(new KeyAdapter()
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
            railwayText.getDocument().addDocumentListener(tell);
            notes.getDocument().addDocumentListener(tell);
        }

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        metadataCaption = caption(I18n.t("loc.ui.settingsMetadata"));
        add(metadataCaption, gbc);

        gbc.gridy++;
        gbc.gridwidth = 1;
        add(caption(I18n.t("loc.ui.labelStartYear")), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        add(startYear, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        add(caption(I18n.t("loc.ui.labelEndYear")), gbc);
        gbc.gridx = 1;
        add(endYear, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        add(caption(I18n.t("loc.ui.labelRailwayName")), gbc);
        gbc.gridx = 1;
        add(railway, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        notesCaption = caption(I18n.t("loc.ui.labelNotes"));
        add(notesCaption, gbc);

        gbc.gridy++;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        add(new JScrollPane(notes), gbc);
    }

    /**
     * Every railway name set on any of these locomotives, trimmed, once each, sorted without regard to case; names
     * differing only in case show the first spelling found.
     */
    static java.util.List<String> railwayNames(java.util.Collection<? extends Locomotive> locomotives)
    {
        java.util.Map<String, String> found = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (Locomotive l : locomotives)
        {
            String name = l.getStructuredNotes().getRailway();

            if (name != null && !name.trim().isEmpty()) found.putIfAbsent(name.trim(), name.trim());
        }

        return new java.util.ArrayList<>(found.values());
    }

    private JLabel caption(String text)
    {
        JLabel l = new JLabel(text);

        captions.add(l);

        return l;
    }

    /**
     * Sets the captions in the owner's label style, without the colon the older dialog puts after them; the Metadata
     * and Notes captions are section headings, blue, over what they name.
     */
    void styleCaptions()
    {
        for (JLabel l : captions)
        {
            l.setText(org.traincontrol.gui.widgets.UiStyle.withoutColon(l.getText()));
            org.traincontrol.gui.widgets.UiStyle.style(l);
        }

        org.traincontrol.gui.widgets.UiStyle.styleHeading(metadataCaption);
        org.traincontrol.gui.widgets.UiStyle.styleHeading(notesCaption);
    }

    /** Shows what the locomotive holds now, and makes that what {@link #isDirty} compares against. */
    void reload(Locomotive l)
    {
        LocomotiveNotes existing = l.getStructuredNotes();

        loadedStart = existing.getStartYear() == 0 ? "" : String.valueOf(existing.getStartYear());
        loadedEnd = existing.getEndYear() == 0 ? "" : String.valueOf(existing.getEndYear());
        loadedRailway = existing.getRailway() == null ? "" : existing.getRailway();
        loadedNotes = existing.getNotes() == null ? "" : existing.getNotes();

        showUnfiltered(startYear, loadedStart);
        showUnfiltered(endYear, loadedEnd);
        railway.setSelectedItem(loadedRailway);
        railwayText.setText(loadedRailway);
        notes.setText(loadedNotes);
        notes.setCaretPosition(0);
    }

    /** Whether anything differs from what {@link #reload} showed. */
    boolean isDirty()
    {
        return !startYear.getText().equals(loadedStart) || !endYear.getText().equals(loadedEnd)
            || !railwayText.getText().equals(loadedRailway) || !notes.getText().equals(loadedNotes);
    }

    /** A stored year past the four digits typing takes (an imported one may be) is shown whole, so it stays saved. */
    private static void showUnfiltered(JTextField year, String text)
    {
        javax.swing.text.AbstractDocument document = (javax.swing.text.AbstractDocument) year.getDocument();
        javax.swing.text.DocumentFilter typing = document.getDocumentFilter();

        document.setDocumentFilter(null);
        year.setText(text);
        document.setDocumentFilter(typing);
    }

    /** Four digits, typed or pasted; the whitespace around a paste ("1950" and a line break) is dropped first. */
    private static JTextField yearField()
    {
        JTextField year = AutonomyEditorPanel.digitsOnlyField(null, 10, 4);
        javax.swing.text.AbstractDocument document = (javax.swing.text.AbstractDocument) year.getDocument();
        javax.swing.text.DocumentFilter digits = document.getDocumentFilter();

        document.setDocumentFilter(new javax.swing.text.DocumentFilter()
        {
            @Override
            public void insertString(FilterBypass fb, int offset, String text, javax.swing.text.AttributeSet attr)
                throws javax.swing.text.BadLocationException
            {
                replace(fb, offset, 0, text, attr);
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, javax.swing.text.AttributeSet attr)
                throws javax.swing.text.BadLocationException
            {
                String trimmed = text == null ? null : text.trim();

                // A space alone is refused, not taken as an empty paste that would clear the selection
                if (trimmed == null || !trimmed.isEmpty() || text.isEmpty()) digits.replace(fb, offset, length, trimmed, attr);
            }
        });

        return year;
    }

    /**
     * A year field as a number, zero when blank: digits of any script, as typing takes them; a stored year's minus sign
     * is dropped.  Read as a long: Integer.MIN_VALUE's digits overflow an int, and the cast returns that one as stored.
     */
    private static int year(JTextField field)
    {
        String digits = field.getText().replaceAll("[^\\p{Nd}]", "");

        return digits.isEmpty() ? 0 : (int) Long.parseLong(digits);
    }

    /** Writes what is shown onto the locomotive. */
    void apply(Locomotive l)
    {
        int start = year(startYear);
        int end = year(endYear);

        String trimmed = railwayText.getText().trim();
        String rail = trimmed.substring(0, Math.min(trimmed.length(), TrainControlUI.MAX_LOC_RAILWAY_NAME));
        String text = notes.getText().substring(0, Math.min(notes.getText().length(), TrainControlUI.MAX_LOC_NOTES_LENGTH));

        l.setStructuredNotes(start, end, rail, text);
    }
}
