package com.mockinterview.exception;

import java.util.List;

public class AIProviderException extends RuntimeException {
    
    private final String providerName;
    private final int httpStatus;
    private final String errorCode;
    private final String errorMessage;
    private final Integer retryAfter;
    private boolean fallbackUsed;
    private Object fallbackData;
    private String selectedInterviewMode;
    private String questionSource;
    private List<AIProviderException> subErrors;

    public AIProviderException(String providerName, int httpStatus, String errorCode, String errorMessage, Integer retryAfter) {
        super(errorMessage);
        this.providerName = providerName;
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.retryAfter = retryAfter;
        this.fallbackUsed = false;
    }

    public AIProviderException(String providerName, int httpStatus, String errorCode, String errorMessage, Integer retryAfter, List<AIProviderException> subErrors) {
        super(errorMessage);
        this.providerName = providerName;
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.retryAfter = retryAfter;
        this.subErrors = subErrors;
        this.fallbackUsed = false;
    }

    public void setFallbackUsed(boolean fallbackUsed) {
        this.fallbackUsed = fallbackUsed;
    }

    public void setFallbackData(Object fallbackData) {
        this.fallbackData = fallbackData;
    }

    public String getProviderName() {
        return providerName;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Integer getRetryAfter() {
        return retryAfter;
    }

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    public Object getFallbackData() {
        return fallbackData;
    }

    public List<AIProviderException> getSubErrors() {
        return subErrors;
    }

    public String getSelectedInterviewMode() {
        return selectedInterviewMode;
    }

    public void setSelectedInterviewMode(String selectedInterviewMode) {
        this.selectedInterviewMode = selectedInterviewMode;
    }

    public String getQuestionSource() {
        return questionSource;
    }

    public void setQuestionSource(String questionSource) {
        this.questionSource = questionSource;
    }
}
