package com.docgalaxy.ui.canvas.layer;

import java.util.List;
import java.util.Set;

/**
 * Specialised {@link RenderLayer} that receives highlight and route state from
 * the canvas controller so it can draw selection halos, route indicators, etc.
 */
public interface OverlayLayer extends RenderLayer {

    /**
     * Updates the set of highlighted note ids.
     * An empty or {@code null} set clears the highlight.
     *
     * @param noteIds ids of notes to highlight
     */
    void setHighlightedNotes(Set<String> noteIds);

    /**
     * Updates the ordered list of note ids that form the navigation route.
     * A {@code null} or empty list clears the route.
     *
     * @param noteIds ordered route node ids
     */
    void setNavigationRoute(List<String> noteIds);
}
