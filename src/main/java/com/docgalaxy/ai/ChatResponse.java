package com.docgalaxy.ai;

public class ChatResponse {
    private final String content;
    private final int tokensUsed;
    private final boolean success;
    private final String errorMessage;

    private ChatResponse(String content, int tokensUsed, boolean success, String errorMessage) {
        this.content = content;
        this.tokensUsed = tokensUsed;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public static ChatResponse success(String content, int tokensUsed) {
        return new ChatResponse(content, tokensUsed, true, null);
    }

    public static ChatResponse failure(String errorMessage) {
        return new ChatResponse(null, 0, false, errorMessage);
    }

    public String getContent() { return content; }
    public int getTokensUsed() { return tokensUsed; }
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
}
