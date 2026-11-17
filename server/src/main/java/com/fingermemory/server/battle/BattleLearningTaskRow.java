package com.fingermemory.server.battle;

public class BattleLearningTaskRow {
    private String ownerId;
    private String battleId;
    private int turnId;
    private String taskId;
    private String sessionId;
    private String studyItemId;
    private boolean energyGranted;
    private String submissionId;
    private long createdAtMs;
    private long updatedAtMs;

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getBattleId() {
        return battleId;
    }

    public void setBattleId(String battleId) {
        this.battleId = battleId;
    }

    public int getTurnId() {
        return turnId;
    }

    public void setTurnId(int turnId) {
        this.turnId = turnId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getStudyItemId() {
        return studyItemId;
    }

    public void setStudyItemId(String studyItemId) {
        this.studyItemId = studyItemId;
    }

    public boolean isEnergyGranted() {
        return energyGranted;
    }

    public void setEnergyGranted(boolean energyGranted) {
        this.energyGranted = energyGranted;
    }

    public String getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(String submissionId) {
        this.submissionId = submissionId;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }

    public void setCreatedAtMs(long createdAtMs) {
        this.createdAtMs = createdAtMs;
    }

    public long getUpdatedAtMs() {
        return updatedAtMs;
    }

    public void setUpdatedAtMs(long updatedAtMs) {
        this.updatedAtMs = updatedAtMs;
    }
}
