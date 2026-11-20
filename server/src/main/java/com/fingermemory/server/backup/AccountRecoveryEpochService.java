package com.fingermemory.server.backup;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AccountRecoveryEpochService {
    static final long RESTORED_VERSION_OFFSET = 1_000_000L;

    private final JdbcTemplate jdbcTemplate;

    public AccountRecoveryEpochService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void activateRestoredGameplay(String ownerId, String newEpoch) {
        jdbcTemplate.update("""
                UPDATE fm_battle
                SET data_epoch = ?,
                    state_json = JSON_SET(
                      state_json,
                      '$.stateVersion',
                      state_version + ?
                    ),
                    state_version = state_version + ?,
                    updated_at_ms = updated_at_ms + 1
                WHERE owner_id = ?
                """,
                newEpoch,
                RESTORED_VERSION_OFFSET,
                RESTORED_VERSION_OFFSET,
                ownerId);
        jdbcTemplate.update("""
                UPDATE fm_adventure_run
                SET data_epoch = ?,
                    run_version = run_version + ?,
                    updated_at_ms = updated_at_ms + 1
                WHERE owner_id = ?
                """,
                newEpoch,
                RESTORED_VERSION_OFFSET,
                ownerId);
    }
}
