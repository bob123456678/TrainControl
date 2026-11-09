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
 * The Multi-Unit tab of the Locomotive Settings window shows it.  A multi-unit the
 * Central Station defines is shown, not edited.
 */
final class MultiUnitPanel extends JPanel
{
    private final boolean readOnly;
    private final JPanel scrollPanel = new JPanel(new GridBagLayout());
    private final JPanel headingPanel = new JPanel(new GridBagLayout());
    private final JPanel top = new JPanel(new java.awt.BorderLayout(6, 0));
    private final JLabel filterLabel = org.traincontrol.gui.widgets.UiStyle.label(
        org.traincontrol.gui.widgets.UiStyle.withoutColon(I18n.t("loc.ui.filterList")));
    private final JTextField filterField = new JTextField();
    private final List<JPanel> rowPanels = new ArrayList<>();
    private final JScrollPane scrollPane = new JScrollPane(scrollPanel);
    private final Runnable onChange;
    private final int reverseWidth;
    private Map<String, Double> loaded;

    /** @param onChange told whenever a tick or a number changes, or null */
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
        gbc.insets = new Insets(5, 4, 5, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;

        top.setOpaque(false);
        top.add(filterLabel, java.awt.BorderLayout.WEST);
        top.add(filterField, java.awt.BorderLayout.CENTER);
        add(top, gbc);

        JLabel nameLabel = org.traincontrol.gui.widgets.UiStyle.label(I18n.t("loc.ui.labelLocomotiveName"));

        JLabel multiplierLabel = org.traincontrol.gui.widgets.UiStyle.label(
            I18n.t("loc.ui.labelSpeedMultiplierPercent"));
        multiplierLabel.setToolTipText(I18n.t("loc.ui.tooltip.adjustRelativeSpeed"));

        JLabel reverseLabel = org.traincontrol.gui.widgets.UiStyle.label(I18n.t("loc.ui.labelReverse"));
        reverseLabel.setToolTipText(I18n.t("loc.ui.tooltip.swapDirection"));

        // A check box paints its box inside its own left inset: the headings over the two box columns start there too
        int box = new JCheckBox().getInsets().left;

        nameLabel.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, box, 0, 0));
        reverseLabel.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, box, 0, 0));

        // Reverse is as wide as its heading needs, in every row, so the heading still starts at the boxes
        reverseWidth = Math.max(com.formdev.flatlaf.util.UIScale.scale(84), reverseLabel.getPreferredSize().width);

        headingPanel.setOpaque(false);
        columns(headingPanel, nameLabel, multiplierLabel, null, reverseLabel);

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
        if (readOnly)
        {
            filterField.setVisible(false);
            filterLabel.setVisible(false);
        }

        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        // Make scrolling faster
        scrollPane.getVerticalScrollBar().setUnitIncrement(scrollPane.getVerticalScrollBar().getUnitIncrement() * 16);

        gbc.gridy = 1;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        add(scrollPane, gbc);

        this.loaded = chosen();
    }

    /** Says what the list is for, as an (i) at the end of the filter's row. */
    void setNote(String note)
    {
        top.add(org.traincontrol.gui.InfoMark.label(note), java.awt.BorderLayout.EAST);
    }

    /** How many pixels wider the list must be to show every column beside its scroll bar (negative: to spare). */
    int shortfall()
    {
        Insets around = scrollPane.getInsets();

        return scrollPanel.getPreferredSize().width + scrollPane.getVerticalScrollBar().getPreferredSize().width
            - (scrollPane.getWidth() - around.left - around.right);
    }

    /**
     * Lays out a heading or a row in the same four columns: name, slider, its value, Reverse.  A null `value` lets the
     * slider's column take the value's room.
     */
    private void columns(JPanel panel, java.awt.Component name, java.awt.Component slider,
        java.awt.Component value, java.awt.Component reverse)
    {
        GridBagConstraints c = new GridBagConstraints();

        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 0, 10);
        c.weightx = 0;
        name.setPreferredSize(new Dimension(com.formdev.flatlaf.util.UIScale.scale(170), name.getPreferredSize().height));
        panel.add(name, c);

        c.gridx = 1;
        c.weightx = 1;
        c.gridwidth = value == null ? 2 : 1;
        panel.add(slider, c);

        c.gridx = 3;
        c.gridwidth = 1;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 0, 0);
        reverse.setPreferredSize(new Dimension(reverseWidth, reverse.getPreferredSize().height));
        panel.add(reverse, c);

        // Added last, so a row's first three components stay the name, the slider and Reverse
        if (value != null)
        {
            c.gridx = 2;
            c.gridwidth = 1;
            c.weightx = 0;
            c.insets = new Insets(0, 0, 0, 10);
            panel.add(value, c);
        }
    }

    private JPanel buildRow(Locomotive loco, Map<String, Double> currentLinkedLocos)
    {
        JPanel rowPanel = new JPanel(new GridBagLayout());

        rowPanel.setOpaque(false);

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
        multiplierSlider.setPaintLabels(false);
        multiplierSlider.setSnapToTicks(false);
        multiplierSlider.setPreferredSize(new Dimension(com.formdev.flatlaf.util.UIScale.scale(150),
            multiplierSlider.getPreferredSize().height));
        multiplierSlider.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        multiplierSlider.setFocusable(true);

        // The value is read beside the slider, which carries ticks only
        JLabel valueLabel = new JLabel(multiplierSlider.getValue() + "%", JLabel.RIGHT);

        valueLabel.setPreferredSize(new Dimension(com.formdev.flatlaf.util.UIScale.scale(44),
            valueLabel.getPreferredSize().height));

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

            valueLabel.setText(multiplierSlider.getValue() + "%");
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

        columns(rowPanel, checkBox, multiplierSlider, valueLabel, reverseCheckBox);

        return rowPanel;
    }

    private void changed()
    {
        if (onChange != null) onChange.run();
    }

    /** Lays out the heading and the rows that match the filter, or says none do. */
    private void render()
    {
        String text = filterField.getText().toLowerCase(java.util.Locale.ROOT);

        scrollPanel.removeAll();

        GridBagConstraints scrollGbc = new GridBagConstraints();
        scrollGbc.insets = new Insets(2, 4, 2, 4);
        scrollGbc.fill = GridBagConstraints.HORIZONTAL;
        scrollGbc.weightx = 1;
        scrollGbc.gridwidth = 3;

        scrollPanel.add(headingPanel, scrollGbc);
        scrollGbc.gridy++;
        scrollPanel.add(Box.createVerticalStrut(2), scrollGbc);

        boolean hasRows = false;

        for (JPanel rowPanel : rowPanels)
        {
            JCheckBox checkBox = (JCheckBox) rowPanel.getComponent(0);

            if (checkBox.getText().toLowerCase(java.util.Locale.ROOT).contains(text))
            {
                scrollGbc.gridy++;
                scrollPanel.add(rowPanel, scrollGbc);
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
}
