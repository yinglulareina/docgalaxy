package com.docgalaxy.ui;

import com.docgalaxy.util.AppConstants;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MainFrame}.
 *
 * <p>Skipped automatically in headless CI environments — {@link JFrame}
 * requires a display and cannot be constructed without one.
 */
class MainFrameTest {

    @BeforeEach
    void requireDisplay() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),
                "Skipping MainFrameTest: no display available (headless environment)");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Creates a MainFrame on the EDT and returns it synchronously. */
    private static MainFrame createOnEdt() throws InvocationTargetException, InterruptedException {
        MainFrame[] box = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> box[0] = new MainFrame());
        return box[0];
    }

    // -----------------------------------------------------------------------
    // Window properties
    // -----------------------------------------------------------------------

    @Test
    void title_isDocGalaxy() throws Exception {
        MainFrame frame = createOnEdt();
        assertEquals("DocGalaxy", frame.getTitle());
    }

    @Test
    void size_matchesAppConstants() throws Exception {
        MainFrame frame = createOnEdt();
        assertEquals(AppConstants.DEFAULT_WINDOW_WIDTH,  frame.getWidth());
        assertEquals(AppConstants.DEFAULT_WINDOW_HEIGHT, frame.getHeight());
    }

    @Test
    void defaultCloseOperation_isExitOnClose() throws Exception {
        MainFrame frame = createOnEdt();
        assertEquals(JFrame.EXIT_ON_CLOSE, frame.getDefaultCloseOperation());
    }

    // -----------------------------------------------------------------------
    // Canvas
    // -----------------------------------------------------------------------

    @Test
    void getCanvas_notNull() throws Exception {
        MainFrame frame = createOnEdt();
        assertNotNull(frame.getCanvas());
    }

    @Test
    void canvas_isInContentPane() throws Exception {
        MainFrame frame = createOnEdt();
        // BorderLayout.CENTER — canvas must be a descendant of the content pane
        var canvas = frame.getCanvas();
        boolean found = false;
        for (var comp : frame.getContentPane().getComponents()) {
            if (comp == canvas) { found = true; break; }
        }
        assertTrue(found, "GalaxyCanvas must be a direct child of the content pane");
    }

    @Test
    void canvas_hasCamera() throws Exception {
        MainFrame frame = createOnEdt();
        assertNotNull(frame.getCanvas().getCamera());
    }
}
