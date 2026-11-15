package com.fingermemory.server.migration;

public final class MigrationDtos {
    private MigrationDtos() {
    }

    public record SnapshotCounts(
            int lexicons,
            int words,
            int studyItems,
            int lexiconItems,
            int notes,
            int practiceAttempts,
            int practiceSessions,
            int reviewStates,
            int reviewLogs) {
    }

    public record MigrationPreview(
            String snapshotFingerprint,
            long targetDataVersion,
            SnapshotCounts counts,
            String schedulerPackageVersion,
            String schedulerModelVersion,
            int schedulerWeights) {
    }

    public record MigrationResult(
            String batchId,
            String snapshotFingerprint,
            long dataVersion,
            SnapshotCounts importedCounts,
            int idMappings) {
    }
}
