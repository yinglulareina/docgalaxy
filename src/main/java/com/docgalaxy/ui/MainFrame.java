package com.docgalaxy.ui;

import com.docgalaxy.ui.canvas.GalaxyCanvas;
import com.docgalaxy.util.AppConstants;

import javax.swing.JFrame;
import java.awt.BorderLayout;

/**
 * The top-level application window for DocGalaxy.
 *
 * <p>Currently hosts only the {@link GalaxyCanvas} in the centre panel.
 * Sidebar and toolbar will be added in a future iteration.
 */
public final class MainFrame extends JFrame {

    private final GalaxyCanvas canvas;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Creates the main window with the galaxy canvas centred inside it.
     *
     * <p><b>Must be called on the Event Dispatch Thread.</b>
     */
    public MainFrame() {
        super("DocGalaxy");

        setSize(AppConstants.DEFAULT_WINDOW_WIDTH, AppConstants.DEFAULT_WINDOW_HEIGHT);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);      // centre on screen

        setLayout(new BorderLayout());
        canvas = new GalaxyCanvas();
        add(canvas, BorderLayout.CENTER);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link GalaxyCanvas} embedded in this frame.
     *
     * @return the canvas; never {@code null}
     */
    public GalaxyCanvas getCanvas() {
        return canvas;
    }
}
