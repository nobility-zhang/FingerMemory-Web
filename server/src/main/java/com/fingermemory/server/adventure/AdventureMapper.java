package com.fingermemory.server.adventure;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AdventureMapper {
    @Select("""
            SELECT owner_id, id, installation_id, namespace_id, book_id,
                   content_version, content_hash, entry_node_id, current_node_id,
                   session_id, companion_key, status, active_slot, run_version,
                   flags_json, deck_json, battle_id, created_at_ms, updated_at_ms
            FROM fm_adventure_run
            WHERE owner_id = #{ownerId} AND active_slot = #{installationId}
            """)
    AdventureRunRow findActive(
            @Param("ownerId") String ownerId,
            @Param("installationId") String installationId);

    @Select("""
            SELECT owner_id, id, installation_id, namespace_id, book_id,
                   content_version, content_hash, entry_node_id, current_node_id,
                   session_id, companion_key, status, active_slot, run_version,
                   flags_json, deck_json, battle_id, created_at_ms, updated_at_ms
            FROM fm_adventure_run
            WHERE owner_id = #{ownerId} AND id = #{runId}
            FOR UPDATE
            """)
    AdventureRunRow findForUpdate(
            @Param("ownerId") String ownerId,
            @Param("runId") String runId);

    @Select("""
            SELECT owner_id, id, installation_id, namespace_id, book_id,
                   content_version, content_hash, entry_node_id, current_node_id,
                   session_id, companion_key, status, active_slot, run_version,
                   flags_json, deck_json, battle_id, created_at_ms, updated_at_ms
            FROM fm_adventure_run
            WHERE owner_id = #{ownerId} AND id = #{runId}
            """)
    AdventureRunRow findById(
            @Param("ownerId") String ownerId,
            @Param("runId") String runId);

    @Select("""
            SELECT owner_id, id, installation_id, namespace_id, book_id,
                   content_version, content_hash, entry_node_id, current_node_id,
                   session_id, companion_key, status, active_slot, run_version,
                   flags_json, deck_json, battle_id, created_at_ms, updated_at_ms
            FROM fm_adventure_run
            WHERE owner_id = #{ownerId} AND active_slot IS NOT NULL
            ORDER BY updated_at_ms DESC, id ASC
            LIMIT #{limit}
            """)
    List<AdventureRunRow> listActive(
            @Param("ownerId") String ownerId,
            @Param("limit") int limit);

    @Insert("""
            INSERT INTO fm_adventure_run (
              owner_id, id, installation_id, namespace_id, book_id,
              content_version, content_hash, entry_node_id, current_node_id,
              session_id, companion_key, status, active_slot, run_version,
              flags_json, deck_json, battle_id, created_at_ms, updated_at_ms
            ) VALUES (
              #{ownerId}, #{id}, #{installationId}, #{namespaceId}, #{bookId},
              #{contentVersion}, #{contentHash}, #{entryNodeId}, #{currentNodeId},
              #{sessionId}, #{companionKey}, #{status}, #{activeSlot},
              #{runVersion}, CAST(#{flagsJson} AS JSON),
              CAST(#{deckJson} AS JSON), #{battleId},
              #{createdAtMs}, #{updatedAtMs}
            )
            """)
    int insertRun(AdventureRunRow row);

    @Update("""
            UPDATE fm_adventure_run
            SET current_node_id = #{row.currentNodeId},
                status = #{row.status},
                active_slot = #{row.activeSlot},
                companion_key = #{row.companionKey},
                run_version = #{row.runVersion},
                flags_json = CAST(#{row.flagsJson} AS JSON),
                deck_json = CAST(#{row.deckJson} AS JSON),
                battle_id = #{row.battleId},
                updated_at_ms = #{row.updatedAtMs}
            WHERE owner_id = #{ownerId}
              AND id = #{id}
              AND run_version = #{expectedVersion}
            """)
    int updateRun(
            @Param("row") AdventureRunRow row,
            @Param("ownerId") String ownerId,
            @Param("id") String id,
            @Param("expectedVersion") long expectedVersion);

    @Update("""
            UPDATE fm_adventure_run
            SET battle_id = #{battleId},
                run_version = run_version + 1,
                updated_at_ms = #{updatedAtMs}
            WHERE owner_id = #{ownerId}
              AND id = #{runId}
              AND run_version = #{runVersion}
              AND battle_id IS NULL
            """)
    int bindBattle(
            @Param("ownerId") String ownerId,
            @Param("runId") String runId,
            @Param("runVersion") long runVersion,
            @Param("battleId") String battleId,
            @Param("updatedAtMs") long updatedAtMs);

    @Select("""
            SELECT owner_id, run_id, node_id, encounter_id, battle_id, created_at_ms
            FROM fm_adventure_battle_binding
            WHERE owner_id = #{ownerId} AND battle_id = #{battleId}
            """)
    AdventureBattleBindingRow findBattleBinding(
            @Param("ownerId") String ownerId,
            @Param("battleId") String battleId);

    @Insert("""
            INSERT INTO fm_adventure_battle_binding (
              owner_id, run_id, node_id, encounter_id, battle_id, created_at_ms
            ) VALUES (
              #{ownerId}, #{runId}, #{nodeId}, #{encounterId}, #{battleId},
              #{createdAtMs}
            )
            """)
    int insertBattleBinding(AdventureBattleBindingRow row);

    @Select("""
            SELECT owner_id, run_id, action_id, request_fingerprint,
                   response_json, run_version, created_at_ms
            FROM fm_adventure_receipt
            WHERE owner_id = #{ownerId}
              AND run_id = #{runId}
              AND action_id = #{actionId}
            """)
    AdventureReceiptRow findReceipt(
            @Param("ownerId") String ownerId,
            @Param("runId") String runId,
            @Param("actionId") String actionId);

    @Insert("""
            INSERT INTO fm_adventure_receipt (
              owner_id, run_id, action_id, request_fingerprint,
              response_json, run_version, created_at_ms
            ) VALUES (
              #{ownerId}, #{runId}, #{actionId}, #{requestFingerprint},
              CAST(#{responseJson} AS JSON), #{runVersion}, #{createdAtMs}
            )
            """)
    int insertReceipt(AdventureReceiptRow row);

    @Insert("""
            INSERT INTO fm_adventure_event (
              owner_id, run_id, event_sequence, event_type, node_id,
              payload_json, created_at_ms
            ) VALUES (
              #{ownerId}, #{runId}, #{eventSequence}, #{eventType}, #{nodeId},
              CAST(#{payloadJson} AS JSON), #{createdAtMs}
            )
            """)
    int insertEvent(
            @Param("ownerId") String ownerId,
            @Param("runId") String runId,
            @Param("eventSequence") long eventSequence,
            @Param("eventType") String eventType,
            @Param("nodeId") String nodeId,
            @Param("payloadJson") String payloadJson,
            @Param("createdAtMs") long createdAtMs);
}
