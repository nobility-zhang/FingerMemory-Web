package com.fingermemory.server.backup;

import com.fingermemory.server.migration.MigrationDtos.SnapshotCounts;

public final class BackupDtos {
    private BackupDtos() {
    }

    public record BackupPreview(
            String fingerprint,
            long exportedAt,
            long sourceDataVersion,
            SnapshotCounts counts,
            int reviewReceipts,
            int rewardEvents,
            int migrationReceipts,
            int idMappings) {
    }

    public record BackupRestoreResult(
            String outcome,
            String fingerprint,
            long dataVersion,
            SnapshotCounts counts) {
    }
}
