package com.docgalaxy.ai.navigator;

import com.docgalaxy.ai.AIServiceException;
import com.docgalaxy.model.Note;
import java.util.List;

public interface NavigatorService {
    NavigationResult navigate(String query, LearningStyle style) throws AIServiceException;
    List<Note> searchSimilar(String query, int topK);
}
