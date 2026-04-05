package com.docgalaxy.ui.canvas.layer;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;

public interface RenderLayer {
    void render(Graphics2D g, AffineTransform cameraTransform, double zoom);
    boolean needsRepaint();
}
