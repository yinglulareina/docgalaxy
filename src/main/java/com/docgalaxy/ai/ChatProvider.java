package com.docgalaxy.ai;

public interface ChatProvider {
    ChatResponse chat(String prompt) throws AIServiceException;
    ChatResponse chatWithSystem(String systemPrompt, String userPrompt) throws AIServiceException;
}
