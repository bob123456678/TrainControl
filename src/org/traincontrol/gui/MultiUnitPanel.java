package org.traincontrol.gui;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import org.traincontrol.base.Locomotive;
import org.traincontrol.util.I18n;

/**
 * The locomotives of one multi-unit as a list: a tick, a speed multiplier and a Reverse tick for each, with a filter.
 * The Set as Multi-Unit dialog and the Multi-Unit tab of the Locomotive Settings window both show it.  A multi-unit the
 * Central Station defines is shown, not edited.
 */
final class MultiUnitPanel extends JPanel
{
    private final boolean readOnly;
    private final JPanel scrollPanel = new JPanel(new GridBagLayout());
    private final JPanel headingPanel = new JPanel(new GridLayout(1, 3));
    private final JTextField filterField = new JTextField();
    private final List<JPanel> rowPanels = new ArrayList<>();
    private final Runnable onChange;
    private Map<String, Double> loaded;

    /**
     * @param ui the main window
     * @param l the multi-unit being shown
     * @param onChange told whenever a tick or a number changes, or null
     */
    MultiUnitPanel(TrainControlUI ui, Locomotive l, Runnable onChange)
    {
        super(new GridBagLayout());

        this.onChange = onChange;

        // If the multi unit is defined in the central station, enter a read-only mode
        this.readOnly = l.getDecoderType() == Locomotive.decoderType.MULTI_UNIT;

        List<Locomotive> allLocomotives = new ArrayList<>(
            !readOnly ? ui.getModel().getLocomotives() : l.getModelMultiUnitLocomotives());
        Map<String, Double> currentLinkedLocos = !readOnly ? l.getLinkedLocomotiveNames() : l.getModelMultiUnitLocomotiveNames();

        // Sort allLocomotives to prioritize currentLinkedLocos and then alphabetically by name
        Collections.sort(allLocomotives, (a, b) ->
        {
            boolean aLinked = currentLinkedLocos.containsKey(a.getName());
            boolean bLinked = currentLinkedLocos.containsKey(b.getName());

            if (aLinked && !bLinked) return -1;
            else if (!aLinked && bLinked) return 1;
            else return a.getName().compareToIgnoreCase(b.getName());
        });

        setFont(new Font("Segoe UI", Font.PLAIN, 12));

        filterField.setToolTipText(I18n.t("loc.ui.tooltip.filterByLocomotiveName"));
        filterField.setName("multi.filter");

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        add(filterField, gbc);

        JLabel nameLabel = new JLabel(I18n.t("loc.ui.labelLocomotiveName"));
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));

        JLabel multiplierLabel = new JLabel(I18n.t("loc.ui.labelSpeedMultiplierPercent"));
        multiplierLabel.setToolTipText(I18n.t("loc.ui.tooltip.adjustRelativeSpeed"));
        multiplierLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));

        JLabel reverseLabel = new JLabel(I18n.t("loc.ui.labelReverse"));
        reverseLabel.setToolTipText(I18n.t("loc.ui.tooltip.swapDirection"));
        reverseLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));

        headingPanel.add(nameLabel);
        headingPanel.add(multiplierLabel);
        headingPanel.add(reverseLabel);

        for (Locomotive loco : allLocomotives)
        {
            if (readOnly || // show all the CS MU's locomotives for informational purposes
                l.canBeLinkedTo(loco, false) || l.isLinkedTo(loco))
            {
                rowPanels.add(buildRow(loco, currentLinkedLocos));
            }
        }

        filterField.getDocument().addDocumentListener(Typing.tell(this::render));

        render();

        // sanity check - should never happen unless CS data is corrupt
        if (readOnly) filterField.setVisible(false);

        JScrollPane scrollPane = new JScrollPane(scrollPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        // Make scrolling faster
        scrollPane.getVerticalScrollBar().setUnitIncrement(scrollPane.getVerticalScrollBar().getUnitIncrement() * 16);

        gbc.gridy = 1;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        add(scrollPane, gbc);

        this.loaded = chosen();
    }

    private JPanel buildRow(Locomotive loco, Map<String, Double> currentLinkedLocos)
    {
        JPanel rowPanel = new JPanel(new GridLayout(1, 3, 10, 5));

        JCheckBox checkBox = new JCheckBox(loco.getName().length() > TrainControlUI.MAX_MU_SELECTOR_LOC_NAME_LENGTH
            ? loco.getName().substring(0, TrainControlUI.MAX_MU_SELECTOR_LOC_NAME_LENGTH) + "..."
            : loco.getName(), currentLinkedLocos.containsKey(loco.getName()));

        checkBox.setToolTipText(loco.getName());
        checkBox.setFocusable(false);

        double multiplierValue = currentLinkedLocos.getOrDefault(loco.getName(), 1.0);
        boolean isReversed = multiplierValue < 0;

        JSlider multiplierSlider = new JSlider(0, 200, (int) (Math.abs(multiplierValue) * 100));
        multiplierSlider.setMajorTickSpacing(50);
        multiplierSlider.setMinorTickSpacing(50);
        multiplierSlider.setPaintTicks(true);
        multiplierSlider.setPaintLabels(true);
        multiplierSlider.setSnapToTicks(false);
        multiplierSlider.setPreferredSize(new Dimension(250, 50));
        multiplierSlider.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        multiplierSlider.setFocusable(true);

        // Custom label table for specific tick labels
        java.util.Hashtable<Integer, JLabel> labelTable = new java.util.Hashtable<>();
        labelTable.put(0, new JLabel("0"));
        labelTable.put(50, new JLabel("50"));
        labelTable.put(100, new JLabel("100"));
        labelTable.put(150, new JLabel("150"));
        labelTable.put(200, new JLabel("200"));
        multiplierSlider.setLabelTable(labelTable);

        // Update tooltip to show selected value on hover
        multiplierSlider.addMouseMotionListener(new MouseMotionAdapter()
        {
            @Override
            public void mouseMoved(MouseEvent e)
            {
                JSlider source = (JSlider) e.getSource();
                source.setToolTipText(String.valueOf(source.getValue()) + "%");
            }
        });

        // Add change listener to jump to 1 if the value is 0
        multiplierSlider.addChangeListener(e ->
        {
            if (multiplierSlider.getValue() == 0) multiplierSlider.setValue(1);

            changed();
        });

        // Add mouse listener to reset to 100 on double-click
        multiplierSlider.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (e.getClickCount() == 2) multiplierSlider.setValue(100);
            }
        });

        JCheckBox reverseCheckBox = new JCheckBox("", isReversed);
        reverseCheckBox.setFocusable(false);

        checkBox.addActionListener(e -> changed());
        reverseCheckBox.addActionListener(e -> changed());

        // Central station multi units are read-only
        if (readOnly)
        {
            checkBox.setSelected(true);
            checkBox.setEnabled(false);
            reverseCheckBox.setEnabled(false);
            multiplierSlider.setEnabled(false);
        }

        rowPanel.add(checkBox);
        rowPanel.add(multiplierSlider);
        rowPanel.add(reverseCheckBox);

        return rowPanel;
    }

    private void changed()
    {
        if (onChange != null) onChange.run();
    }

    /** Lays out the heading and the rows that match the filter, or says none do. */
    private void render()
    {
        String text = filterField.getText().toLowerCase();

        scrollPanel.removeAll();

        GridBagConstraints scrollGbc = new GridBagConstraints();
        scrollGbc.insets = new Insets(5, 5, 5, 5);
        scrollGbc.fill = GridBagConstraints.HORIZONTAL;
        scrollGbc.weightx = 1;
        scrollGbc.gridwidth = 3;

        scrollPanel.add(headingPanel, scrollGbc);
        scrollGbc.gridy++;
        scrollPanel.add(Box.createVerticalStrut(10), scrollGbc);

        boolean hasRows = false;

        for (JPanel rowPanel : rowPanels)
        {
            JCheckBox checkBox = (JCheckBox) rowPanel.getComponent(0);

            if (checkBox.getText().toLowerCase().contains(text))
            {
                scrollGbc.gridy++;
                scrollPanel.add(rowPanel, scrollGbc);
                scrollGbc.gridy++;
                scrollPanel.add(Box.createVerticalStrut(10), scrollGbc);
                hasRows = true;
            }
        }

        headingPanel.setVisible(hasRows);

        if (!hasRows)
        {
            scrollGbc.gridy++;
            scrollPanel.add(new JLabel(I18n.t("loc.ui.labelNoLocomotivesFound")), scrollGbc);
        }

        // Pushes the rows to the top when there are few
        scrollGbc.gridy++;
        scrollGbc.weighty = 1;
        scrollPanel.add(Box.createGlue(), scrollGbc);

        scrollPanel.revalidate();
        scrollPanel.repaint();
    }

    /** Whether the list is the Central Station's, shown and not edited. */
    boolean isReadOnly()
    {
        return readOnly;
    }

    /**
     * The locomotives ticked, each with its multiplier (negative when Reverse is ticked).  Read from every row, not
     * only the ones the filter shows.
     */
    Map<String, Double> chosen()
    {
        Map<String, Double> out = new LinkedHashMap<>();

        for (JPanel rowPanel : rowPanels)
        {
            JCheckBox checkBox = (JCheckBox) rowPanel.getComponent(0);
            JSlider multiplierSlider = (JSlider) rowPanel.getComponent(1);
            JCheckBox reverseCheckBox = (JCheckBox) rowPanel.getComponent(2);

            if (checkBox.isSelected())
            {
                double multiplier = multiplierSlider.getValue() / 100.0;

                if (reverseCheckBox.isSelected()) multiplier = -multiplier;

                if (multiplier != 0 && multiplier >= -2 && multiplier <= 2)
                {
                    out.put(checkBox.getToolTipText(), multiplier);
                }
            }
        }

        return new HashMap<>(out);
    }

    /** Whether the list differs from what it showed when it was built. */
    boolean isDirty()
    {
        return !readOnly && !chosen().equals(loaded);
    }

    /** Gives the filter the keyboard. */
    void focusFilter()
    {
        filterField.requestFocusInWindow();
    }
}
