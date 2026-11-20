package com.fingermemory.server.backup;

import com.fingermemory.server.migration.MigrationDtos.SnapshotCounts;
import java.util.Map;

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
            int idMappings,
            Map<String, Integer> serverTables,
            int contentResources) {
    }

    public record BackupRestoreResult(
            String outcome,
            String fingerprint,
            long dataVersion,
            String dataEpoch,
            SnapshotCounts counts,
            Map<String, Integer> serverTables,
            int contentResources) {
    }
}
