package com.docgalaxy.ai;

public class AIServiceException extends Exception {
    private final int statusCode;
    private final boolean retryable;

    public AIServiceException(String message, int statusCode, boolean retryable) {
        super(message);
        this.statusCode = statusCode;
        this.retryable = retryable;
    }

    public AIServiceException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.retryable = false;
    }

    public int getStatusCode() { return statusCode; }
    public boolean isRetryable() { return retryable; }
}
