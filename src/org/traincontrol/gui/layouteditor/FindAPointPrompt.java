package org.traincontrol.gui.layouteditor;

import java.awt.BorderLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.util.I18n;

/**
 * The small prompt of the editor's Find a Point... (FR-055): a text box over the named points of every page, narrowed
 * as typed with capitals ignored.  Enter or a double-click chooses, Escape cancels.  Hangs off the editor window and
 * leaves nothing behind in it.
 */
final class FindAPointPrompt extends JDialog
{
    // The look is installed before the first window of this class is built, as every window's is
    static
    {
        if (!java.awt.GraphicsEnvironment.isHeadless()) org.traincontrol.gui.TrainControlUI.installLookAndFeel();
    }

    /** One named point: where it is and what it is called. */
    static final class Entry
    {
        final TileKey tile;
        final String name;

        Entry(TileKey tile, String name)
        {
            this.tile = tile;
            this.name = name;
        }

        /** The row as the user reads it: the page is always named, as two pages can hold the same name. */
        @Override
        public String toString()
        {
            return I18n.f("layout.ui.findPointRow", name, tile.getPage());
        }
    }

    private final List<Entry> all;
    private final Consumer<TileKey> chosen;
    private final JTextField box = new JTextField(24);
    private final DefaultListModel<Entry> shown = new DefaultListModel<>();
    private final JList<Entry> list = new JList<>(shown);
    private final JLabel status = new JLabel(" ");

    FindAPointPrompt(java.awt.Frame owner, List<Entry> points, Consumer<TileKey> chosen)
    {
        super(owner, I18n.t("layout.ui.findPointTitle"), false);

        this.all = new ArrayList<>(points);
        this.chosen = chosen;

        all.sort((a, b) ->
        {
            int byName = a.name.compareToIgnoreCase(b.name);

            return byName != 0 ? byName : a.tile.getPage().compareToIgnoreCase(b.tile.getPage());
        });

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        org.traincontrol.gui.TrainControlUI.applyWindowIcon(this);

        JPanel content = new JPanel(new BorderLayout(6, 6));

        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel top = new JPanel(new BorderLayout(6, 0));

        top.add(org.traincontrol.gui.widgets.UiStyle.label(org.traincontrol.gui.widgets.UiStyle.withoutColon(
            I18n.t("layout.ui.findPointLabel"))), BorderLayout.WEST);
        top.add(box, BorderLayout.CENTER);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(8);
        list.setFocusable(false);

        JScrollPane scroll = new JScrollPane(list);

        content.add(top, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        content.add(status, BorderLayout.SOUTH);

        setContentPane(content);

        box.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
        {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e)
            {
                narrow();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e)
            {
                narrow();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e)
            {
                narrow();
            }
        });

        box.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyPressed(KeyEvent e)
            {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
                {
                    e.consume();
                    dispose();
                }
                else if (e.getKeyCode() == KeyEvent.VK_ENTER)
                {
                    e.consume();
                    choose();
                }
                else if (e.getKeyCode() == KeyEvent.VK_DOWN || e.getKeyCode() == KeyEvent.VK_UP)
                {
                    e.consume();
                    step(e.getKeyCode() == KeyEvent.VK_DOWN ? 1 : -1);
                }
            }
        });

        list.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (e.getClickCount() < 2) return;

                int row = list.locationToIndex(e.getPoint());

                if (row >= 0 && list.getCellBounds(row, row).contains(e.getPoint()))
                {
                    list.setSelectedIndex(row);
                    choose();
                }
            }
        });

        narrow();
        pack();
        setLocationRelativeTo(owner);
    }

    /** The points whose name contains the text, capitals ignored; every point when the box is empty. */
    private void narrow()
    {
        String wanted = box.getText().trim().toLowerCase();

        shown.clear();

        for (Entry entry : all)
        {
            if (wanted.isEmpty() || entry.name.toLowerCase().contains(wanted)) shown.addElement(entry);
        }

        if (!shown.isEmpty()) list.setSelectedIndex(0);

        if (all.isEmpty()) status.setText(I18n.t("layout.ui.findPointNoPoints"));
        else if (shown.isEmpty()) status.setText(I18n.f("layout.ui.findPointNoMatch", box.getText().trim()));
        else status.setText(I18n.f("layout.ui.findPointCount", shown.size()));
    }

    private void step(int by)
    {
        if (shown.isEmpty()) return;

        int next = Math.max(0, Math.min(shown.size() - 1, list.getSelectedIndex() + by));

        list.setSelectedIndex(next);
        list.ensureIndexIsVisible(next);
    }

    /** Closes the prompt and goes to the selected point; with nothing to choose, Enter does nothing. */
    private void choose()
    {
        Entry entry = list.getSelectedValue();

        if (entry == null) return;

        dispose();

        chosen.accept(entry.tile);
    }
}
