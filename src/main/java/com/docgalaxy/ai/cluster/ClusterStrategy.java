package com.docgalaxy.ai.cluster;

import com.docgalaxy.model.Vector2D;
import java.util.List;

public interface ClusterStrategy {
    List<Cluster> cluster(List<Vector2D> points, List<String> noteIds);
}
