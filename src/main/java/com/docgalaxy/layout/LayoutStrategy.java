package com.docgalaxy.layout;

import com.docgalaxy.model.Vector2D;
import java.util.List;
import java.util.Map;

public interface LayoutStrategy {
    Map<String, Vector2D> calculate(List<NodeData> nodes);
    boolean isIterative();
    String getName();
}
