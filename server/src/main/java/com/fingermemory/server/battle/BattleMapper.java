package com.fingermemory.server.battle;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BattleMapper {
    @Insert("""
            INSERT INTO fm_battle (
              owner_id, battle_id, rules_version, state_version, phase, turn_id,
              next_event_sequence, state_json, created_at_ms, updated_at_ms
            ) VALUES (
              #{ownerId}, #{battleId}, #{rulesVersion}, #{stateVersion}, #{phase}, #{turnId},
              #{nextEventSequence}, CAST(#{stateJson} AS JSON), #{createdAtMs}, #{updatedAtMs}
            )
            """)
    int insertBattle(
            @Param("ownerId") String ownerId,
            @Param("battleId") String battleId,
            @Param("rulesVersion") String rulesVersion,
            @Param("stateVersion") long stateVersion,
            @Param("phase") String phase,
            @Param("turnId") int turnId,
            @Param("nextEventSequence") int nextEventSequence,
            @Param("stateJson") String stateJson,
            @Param("createdAtMs") long createdAtMs,
            @Param("updatedAtMs") long updatedAtMs);

    @Select("""
            SELECT owner_id AS ownerId, battle_id AS battleId, rules_version AS rulesVersion,
                   state_version AS stateVersion, phase, turn_id AS turnId,
                   next_event_sequence AS nextEventSequence, state_json AS stateJson,
                   created_at_ms AS createdAtMs, updated_at_ms AS updatedAtMs
            FROM fm_battle
            WHERE owner_id = #{ownerId} AND battle_id = #{battleId}
            """)
    BattleRow findBattle(
            @Param("ownerId") String ownerId,
            @Param("battleId") String battleId);

    @Select("""
            SELECT owner_id AS ownerId, battle_id AS battleId, rules_version AS rulesVersion,
                   state_version AS stateVersion, phase, turn_id AS turnId,
                   next_event_sequence AS nextEventSequence, state_json AS stateJson,
                   created_at_ms AS createdAtMs, updated_at_ms AS updatedAtMs
            FROM fm_battle
            WHERE owner_id = #{ownerId} AND battle_id = #{battleId}
            FOR UPDATE
            """)
    BattleRow lockBattle(
            @Param("ownerId") String ownerId,
            @Param("battleId") String battleId);

    @Update("""
            UPDATE fm_battle
            SET state_version = #{nextStateVersion},
                phase = #{phase},
                turn_id = #{turnId},
                next_event_sequence = #{nextEventSequence},
                state_json = CAST(#{stateJson} AS JSON),
                updated_at_ms = #{updatedAtMs}
            WHERE owner_id = #{ownerId}
              AND battle_id = #{battleId}
              AND state_version = #{expectedStateVersion}
            """)
    int updateBattle(
            @Param("ownerId") String ownerId,
            @Param("battleId") String battleId,
            @Param("expectedStateVersion") long expectedStateVersion,
            @Param("nextStateVersion") long nextStateVersion,
            @Param("phase") String phase,
            @Param("turnId") int turnId,
            @Param("nextEventSequence") int nextEventSequence,
            @Param("stateJson") String stateJson,
            @Param("updatedAtMs") long updatedAtMs);

    @Insert("""
            INSERT INTO fm_battle_learning_task (
              owner_id, battle_id, turn_id, task_id, session_id, study_item_id,
              energy_granted, submission_id, created_at_ms, updated_at_ms
            ) VALUES (
              #{ownerId}, #{battleId}, #{turnId}, #{taskId}, #{sessionId}, #{studyItemId},
              FALSE, NULL, #{createdAtMs}, #{updatedAtMs}
            )
            """)
    int insertLearningTask(
            @Param("ownerId") String ownerId,
            @Param("battleId") String battleId,
            @Param("turnId") int turnId,
            @Param("taskId") String taskId,
            @Param("sessionId") String sessionId,
            @Param("studyItemId") String studyItemId,
            @Param("createdAtMs") long createdAtMs,
            @Param("updatedAtMs") long updatedAtMs);

    @Select("""
            SELECT owner_id AS ownerId, battle_id AS battleId, turn_id AS turnId,
                   task_id AS taskId, session_id AS sessionId, study_item_id AS studyItemId,
                   energy_granted AS energyGranted, submission_id AS submissionId,
                   created_at_ms AS createdAtMs, updated_at_ms AS updatedAtMs
            FROM fm_battle_learning_task
            WHERE owner_id = #{ownerId} AND battle_id = #{battleId} AND turn_id = #{turnId}
            """)
    BattleLearningTaskRow findLearningTask(
            @Param("ownerId") String ownerId,
            @Param("battleId") String battleId,
            @Param("turnId") int turnId);

    @Update("""
            UPDATE fm_battle_learning_task
            SET energy_granted = TRUE,
                submission_id = #{submissionId},
                updated_at_ms = #{updatedAtMs}
            WHERE owner_id = #{ownerId}
              AND battle_id = #{battleId}
              AND turn_id = #{turnId}
              AND task_id = #{taskId}
              AND energy_granted = FALSE
            """)
    int grantLearningEnergy(
            @Param("ownerId") String ownerId,
            @Param("battleId") String battleId,
            @Param("turnId") int turnId,
            @Param("taskId") String taskId,
            @Param("submissionId") String submissionId,
            @Param("updatedAtMs") long updatedAtMs);

    @Insert("""
            INSERT INTO fm_battle_receipt (
              owner_id, battle_id, action_id, request_fingerprint, response_json,
              state_version, created_at_ms
            ) VALUES (
              #{ownerId}, #{battleId}, #{actionId}, #{fingerprint},
              CAST(#{responseJson} AS JSON), #{stateVersion}, #{createdAtMs}
            )
            """)
    int insertReceipt(
            @Param("ownerId") String ownerId,
            @Param("battleId") String battleId,
            @Param("actionId") String actionId,
            @Param("fingerprint") String fingerprint,
            @Param("responseJson") String responseJson,
            @Param("stateVersion") long stateVersion,
            @Param("createdAtMs") long createdAtMs);

    @Select("""
            SELECT owner_id AS ownerId, battle_id AS battleId, action_id AS actionId,
                   request_fingerprint AS requestFingerprint, response_json AS responseJson,
                   state_version AS stateVersion, created_at_ms AS createdAtMs
            FROM fm_battle_receipt
            WHERE owner_id = #{ownerId}
              AND battle_id = #{battleId}
              AND action_id = #{actionId}
            """)
    BattleReceiptRow findReceipt(
            @Param("ownerId") String ownerId,
            @Param("battleId") String battleId,
            @Param("actionId") String actionId);

    @Insert("""
            INSERT INTO fm_battle_event (
              owner_id, battle_id, event_sequence, state_version,
              event_type, event_json, created_at_ms
            ) VALUES (
              #{ownerId}, #{battleId}, #{eventSequence}, #{stateVersion},
              #{eventType}, CAST(#{eventJson} AS JSON), #{createdAtMs}
            )
            """)
    int insertEvent(
            @Param("ownerId") String ownerId,
            @Param("battleId") String battleId,
            @Param("eventSequence") int eventSequence,
            @Param("stateVersion") long stateVersion,
            @Param("eventType") String eventType,
            @Param("eventJson") String eventJson,
            @Param("createdAtMs") long createdAtMs);

    @Select("""
            SELECT owner_id AS ownerId, battle_id AS battleId,
                   event_sequence AS eventSequence, state_version AS stateVersion,
                   event_type AS eventType, event_json AS eventJson,
                   created_at_ms AS createdAtMs
            FROM fm_battle_event
            WHERE owner_id = #{ownerId} AND battle_id = #{battleId}
            ORDER BY event_sequence ASC
            """)
    List<BattleEventRow> listEvents(
            @Param("ownerId") String ownerId,
            @Param("battleId") String battleId);
}
