package org.traincontrol.gui;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JPanel;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.widgets.PositionAwareJFrame;
import org.traincontrol.util.I18n;

/**
 * Popup representing a track diagram
 * @author Adam
 */
public class LayoutPopupUI extends PositionAwareJFrame
{
    private final TrainControlUI parent;
    private final int size;
    private LayoutDiagram layout;
    private int pageIndex;
    
    /**
     * Popup window showing train layouts
     * @param l reference to the layout
     * @param size size of each tile, in pixels
     * @param ui
     * @param pageIndex
     */
    public LayoutPopupUI(LayoutDiagram l, int size, TrainControlUI ui, int pageIndex)
    {
        initComponents();
        
        this.ExtLayoutPanel.setLayout(new FlowLayout());
        this.parent = ui;
        this.size = size;
        this.layout = l;
        this.pageIndex = pageIndex;
    }

    // The grid on screen now, so that the next one can tell it to stop
    private LayoutGrid grid;
    
    /**
     * Draws the page into the window.
     *
     * @param fit whether to size the window to the page and keep where it stands: true when the window is opened and when
     *        its page is changed or refreshed, false for a redraw of the captions alone, which leaves the size and the
     *        place the operator gave it, and saves nothing (OB-341)
     */
    private void drawGrid(boolean fit)
    {
        // The grid being replaced is told to stop first.
        //
        // A LayoutGrid keeps two Swing timers - one that shows the diagram when its tiles have
        // decoded, one that puts a spinner up if they are slow - and both go on firing into the panel
        // after the panel has been emptied.  The outgoing grid then drops a spinner into the page the
        // NEW grid has just drawn.  Both other places that build a grid over an existing panel call
        // this; this one did not.
        if (this.grid != null) this.grid.discard();

        // NOT EMPTIED HERE - the grid does it, at the right moment.
        //
        // This is the position the flicker fix moved `removeAll` OUT of: emptying the panel before
        // the build leaves it blank for the whole of it, and the build is where the image decoding
        // is.  Doing it here made the constructor's own swap a no-op for this window, so the popup
        // kept the flicker while the two other diagram windows lost it.

        LayoutGrid grid = new LayoutGrid(this.layout, size,
            this.ExtLayoutPanel, 
            this,
            true, parent);
        
        this.grid = grid;

        // Half a square at a time on both axes, as the main window's diagram (MT-726, MT-727)
        LayoutGrid.scrollHalfASquareAtATime(this.jScrollPane1, size);

        setTitle(titleWith(this.parent.getWindowTitleString()));

        // A caption redraw is not a new page: the window stays as the operator left it, and what is remembered for it is
        // theirs.  Packing it to its natural size, and saving that, undid a size they had chosen on every caption gesture
        // in the autonomy editor on this page (OB-341).
        if (fit)
        {
            // Scale the popup according to the size of the layout
            this.setPreferredSize(new Dimension(grid.maxWidth + 100, grid.maxHeight + 100));
            pack();

            // Remember window location for different layouts and sizes
            this.setWindowIndex(this.layout.getName()+ "_" + this.getLayoutSize());

            // Only load location once
            if (!this.isLoaded())
            {
                loadWindowBounds();
            }

            saveWindowBounds();
        }
        
        // Update auto layout station labels on the track diagram
        this.parent.updateVisiblePoints();
    }
    
    /**
     * Whether the setup still gives every square of the grid on screen the caption it was drawn with, and every caption its
     * name (OB-341).
     *
     * @return false where it does not, and for a window not drawn yet
     */
    boolean captionsAreAsBuilt()
    {
        return this.grid != null && this.grid.captionsAreAsBuilt();
    }

    public String getLayoutTitle()
    {
        return this.layout.getName();
    }

    /**
     * The window's title: the page's name, the autonomy configuration that is loaded in brackets when one is (FR-127), then
     * the locomotive text the main window adds.
     *
     * @param locomotive what follows the page: the active locomotive's text, or ""
     * @return the title
     */
    String titleWith(String locomotive)
    {
        String configuration = this.parent.getActiveDiagramConfiguration();

        return (configuration == null ? getLayoutTitle()
            : I18n.f("app.ui.windowPopupTitleConfiguration", getLayoutTitle(), configuration)) + locomotive;
    }

    /**
     * Sets the title again, for a configuration loaded, switched, renamed or unloaded while the window is open (FR-127).
     * Event thread only.
     */
    void refreshTitle()
    {
        setTitle(titleWith(this.parent.getWindowTitleString()));
    }
    
    public void render()
    {        
        this.setAlwaysOnTop(parent.isAlwaysOnTop());
                      
        drawGrid(true);
             
        setVisible(true);
        
        // Hide the window on close so that LayoutLabels know they can be deleted
        addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
                e.getComponent().setVisible(false);
            }
        });
    }
    
    /**
     * Refreshes the contents of the layout
     */
    public void refreshDiagram()
    {
        this.goToLayoutPage(this.pageIndex);
    }

    /**
     * Draws the page again for the captions on it alone, keeping the window's size and place and saving nothing
     * (OB-341).
     */
    void refreshCaptions()
    {
        this.goToLayoutPage(this.pageIndex, false);
    }
    
    /**
     * Updates the layout page
     * @param index
     */
    public void goToLayoutPage(int index)
    {
        this.goToLayoutPage(index, true);
    }

    /**
     * Updates the layout page.
     *
     * @param index the page
     * @param fit whether to size the window to it, as `drawGrid` says
     */
    private void goToLayoutPage(int index, boolean fit)
    {
        javax.swing.SwingUtilities.invokeLater( () -> 
        {
            int page = index + 1;

            if (index < this.parent.getModel().getLayoutList().size() && index >= 0)
            {
                this.parent.getModel().logf("layout.ui.logPopupLayoutUpdate", page);

                this.layout = this.parent.getModel().getLayout(
                    this.parent.getModel().getLayoutList().get(index)
                );

                drawGrid(fit);
                this.repaint();

                // Update saved index
                this.pageIndex = index;
            }
            else
            {
                this.parent.getModel().logf("layout.ui.logPopupLayoutPageNotExist", page);
            }
        });
    }
    
    public JPanel getPanel()
    {
        return this.ExtLayoutPanel;
    }
    
    public int getLayoutSize()
    {
        return this.size;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        ExtLayoutPanel = new javax.swing.JPanel();

        setIconImage(Toolkit.getDefaultToolkit().getImage(TrainControlUI.class.getResource("resources/locicon.png")));
        setMinimumSize(new java.awt.Dimension(150, 150));
        addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                formKeyPressed(evt);
            }
        });

        ExtLayoutPanel.setBackground(new java.awt.Color(255, 255, 255));
        ExtLayoutPanel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                ExtLayoutPanelMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout ExtLayoutPanelLayout = new javax.swing.GroupLayout(ExtLayoutPanel);
        ExtLayoutPanel.setLayout(ExtLayoutPanelLayout);
        ExtLayoutPanelLayout.setHorizontalGroup(
            ExtLayoutPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 368, Short.MAX_VALUE)
        );
        ExtLayoutPanelLayout.setVerticalGroup(
            ExtLayoutPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 266, Short.MAX_VALUE)
        );

        jScrollPane1.setViewportView(ExtLayoutPanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void formKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_formKeyPressed
        parent.childWindowKeyEvent(evt);
    }//GEN-LAST:event_formKeyPressed

    private void ExtLayoutPanelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_ExtLayoutPanelMouseClicked
        if (evt.getButton() == MouseEvent.BUTTON3)
        {
            // Same as the main window: the menu carries the setup now, so it is offered whether or not
            // a layout has been built yet
            LayoutRightclickAutonomyMenu.showFor(parent, null, null,
                evt.getComponent(), evt.getX(), evt.getY());
        }
    }//GEN-LAST:event_ExtLayoutPanelMouseClicked

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel ExtLayoutPanel;
    private javax.swing.JScrollPane jScrollPane1;
    // End of variables declaration//GEN-END:variables
}
