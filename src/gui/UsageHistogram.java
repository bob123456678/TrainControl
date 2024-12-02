package gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.JComponent;
import util.Conversion;

/**
 * A basic locomotive usage graph
 */
public class UsageHistogram extends javax.swing.JFrame
{
    TrainControlUI tcui;
    JComponent chart;
    long offset = 0;
    final int perPage = 30;
    
    /** 
     * Creates new form UsageHistogram
     * @param tcui 
     */
    public UsageHistogram(TrainControlUI tcui)
    {
        initComponents();
        this.tcui = tcui;
        this.createHistogramPanel();
        this.setLocationRelativeTo(tcui);
        
        setAlwaysOnTop(tcui.isAlwaysOnTop());
        toFront();
        requestFocus();
    }

    private void createHistogramPanel()
    {
        if (chart != null)
        {
            remove(chart);
        }
        
        TreeMap<String, Long> data = tcui.getModel().getDailyRuntimeStats(perPage, offset);
        TreeMap<String, Integer> dataLocs = tcui.getModel().getDailyCountStats(perPage, offset);
                
        setLayout(new BorderLayout());
        
        // Create a custom component for drawing the histogram
        chart = new JComponent()
        {
            @Override
            protected void paintComponent(Graphics g)
            {
                super.paintComponent(g);

                // Set up drawing parameters
                int barWidth = 20;
                int barHeight = 130;
                int x = 30;
                int y = 200;
                
                // Draw a white background for the chart rectangle
                g.setColor(Color.WHITE);
                g.fillRect(x - 3, y - 150 - 20, 900, 170); // Adjust dimensions as needed
                
                double maxVal = 0;
                
                for (Map.Entry<String, Long> entry : data.entrySet())
                {
                    if (entry.getValue() > maxVal)
                    {
                        maxVal = entry.getValue();
                    }
                }
                
                long cumulativeHours = 0;

                for (Map.Entry<String, Long> entry : data.entrySet())
                {
                    String date = entry.getKey();
                    double value = 0;

                    // Our main stats
                    Integer locCount = dataLocs.getOrDefault(date, 0);

                    String hours = Conversion.convertSecondsToHMm(entry.getValue());
                    cumulativeHours += entry.getValue();

                    if (maxVal > 0)
                    {
                        value = barHeight * (entry.getValue() / maxVal);
                    }

                    // Draw a bar for each data point
                    g.setColor(Color.BLUE);
                    g.fillRect(x + 4, y - (int) value, barWidth, (int) value);

                    // Draw date labels
                    g.setColor(Color.BLACK);
                    Graphics2D g2d = (Graphics2D) g;
                    g2d.setFont(new Font("Tahoma", Font.BOLD, 12)); 
                    g2d.rotate(Math.toRadians(270), x + barWidth / 2.0 + 8, y + 63);
                    g2d.drawString(date, x + 2, y + 63);
                    g2d.rotate(-Math.toRadians(270), x + barWidth / 2.0 + 8, y + 63);

                    // Draw value labels
                    if (entry.getValue() > 0)
                    {
                        g.setColor(Color.BLACK);
                        g.drawString(hours, x + barWidth / 2 - 9, y - (int) value - 5);

                        // Locomotive counts
                        g.drawString(String.valueOf("(" + locCount + ")"), x + barWidth / 2 - 6, y - (int) value - 25);
                    }  
                    
                    // Move to the next position
                    x += barWidth + 10;
                }
                
                // Draw a 1-pixel border around the entire chart
                g.setColor(Color.BLACK);
                g.drawRect(x - 930 + 25, y - 170, 900, 170);
                
                // Add rotated y-axis label
                g.setColor(Color.BLACK);
                Graphics2D g2d = (Graphics2D) g;
                g2d.rotate(Math.toRadians(270), 17, y / 2 + 65);
                g2d.setFont(new Font("Tahoma", Font.BOLD, 12)); 
                g2d.drawString("Hours (# Locs)", 32, y / 2 + 65);
                g2d.rotate(-Math.toRadians(270), 17, y / 2 + 65); 
                
                int numLocs = tcui.getModel().getTotalLocStats(perPage, offset);
                
                setTitle(String.format("Cumulative Runtime: %s locomotive%s, %s hours over past %s to %s days", 
                    numLocs, 
                    numLocs != 1 ? "s" : "",
                    Conversion.convertSecondsToHMm(cumulativeHours),        
                    offset, 
                    offset + perPage
                ));
            }
        };

        add(chart, BorderLayout.CENTER);
        getContentPane().setBackground(new Color(240, 240, 240)); // Set background color
        
        this.pack();
        this.setVisible(true);
    }
    
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        prev = new javax.swing.JButton();
        next = new javax.swing.JButton();
        reset = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Cumulative Locomotive Runtime - Past 30 Days");
        setBackground(new java.awt.Color(246, 246, 246));
        setIconImage(java.awt.Toolkit.getDefaultToolkit().getImage(TrainControlUI.class.getResource("resources/locicon.png")));
        setMaximumSize(new java.awt.Dimension(960, 332));
        setMinimumSize(new java.awt.Dimension(960, 332));
        setPreferredSize(new java.awt.Dimension(960, 332));
        setResizable(false);

        prev.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        prev.setText("<<<");
        prev.setFocusable(false);
        prev.setMaximumSize(new java.awt.Dimension(90, 20));
        prev.setMinimumSize(new java.awt.Dimension(90, 20));
        prev.setPreferredSize(new java.awt.Dimension(90, 20));
        prev.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                prevActionPerformed(evt);
            }
        });

        next.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        next.setText(">>>");
        next.setFocusable(false);
        next.setMaximumSize(new java.awt.Dimension(90, 20));
        next.setMinimumSize(new java.awt.Dimension(90, 20));
        next.setPreferredSize(new java.awt.Dimension(90, 20));
        next.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nextActionPerformed(evt);
            }
        });

        reset.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        reset.setText("Reset");
        reset.setFocusable(false);
        reset.setMaximumSize(new java.awt.Dimension(90, 20));
        reset.setMinimumSize(new java.awt.Dimension(90, 20));
        reset.setPreferredSize(new java.awt.Dimension(90, 20));
        reset.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(23, 23, 23)
                .addComponent(prev, javax.swing.GroupLayout.PREFERRED_SIZE, 78, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(reset, javax.swing.GroupLayout.PREFERRED_SIZE, 78, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(next, javax.swing.GroupLayout.PREFERRED_SIZE, 78, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(671, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(prev, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(next, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(reset, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap(320, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void prevActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_prevActionPerformed
        offset += perPage;
        createHistogramPanel();
    }//GEN-LAST:event_prevActionPerformed

    private void nextActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nextActionPerformed
        
        if (offset >= perPage)
        {
            offset -= perPage;
            createHistogramPanel();
        }
    }//GEN-LAST:event_nextActionPerformed

    private void resetActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetActionPerformed
        offset = 0;
        createHistogramPanel();
    }//GEN-LAST:event_resetActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton next;
    private javax.swing.JButton prev;
    private javax.swing.JButton reset;
    // End of variables declaration//GEN-END:variables

}
