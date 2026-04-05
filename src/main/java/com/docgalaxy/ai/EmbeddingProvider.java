package com.docgalaxy.ai;

import java.util.List;

public interface EmbeddingProvider {
    double[] embed(String text) throws AIServiceException;
    List<double[]> batchEmbed(List<String> texts) throws AIServiceException;
    int getDimension();
    String getModelName();
}
