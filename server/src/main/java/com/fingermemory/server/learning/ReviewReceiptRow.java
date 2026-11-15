package com.fingermemory.server.learning;

public class ReviewReceiptRow {
    private String submissionId;
    private String requestFingerprint;
    private String responseJson;
    private long reviewedAtMs;

    public String getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(String submissionId) {
        this.submissionId = submissionId;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public void setRequestFingerprint(String requestFingerprint) {
        this.requestFingerprint = requestFingerprint;
    }

    public String getResponseJson() {
        return responseJson;
    }

    public void setResponseJson(String responseJson) {
        this.responseJson = responseJson;
    }

    public long getReviewedAtMs() {
        return reviewedAtMs;
    }

    public void setReviewedAtMs(long reviewedAtMs) {
        this.reviewedAtMs = reviewedAtMs;
    }
}
