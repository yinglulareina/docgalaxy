package com.docgalaxy.ui.canvas;

import java.util.List;
import java.util.Set;

public interface CanvasController {
    void highlightNotes(Set<String> noteIds);
    void navigateToSector(String sectorId);
    void navigateToNote(String noteId);
    void clearHighlight();
    void showNavigationRoute(List<String> noteIds);
    double getZoomLevel();
    void fitAll();
}
