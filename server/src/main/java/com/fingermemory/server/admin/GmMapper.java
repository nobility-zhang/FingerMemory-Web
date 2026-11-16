package com.fingermemory.server.admin;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GmMapper {
    @Select("""
            SELECT id, username, display_name, role, status, account_kind, version
            FROM fm_user
            WHERE (#{cursor} IS NULL OR username_normalized > #{cursor})
            ORDER BY username_normalized
            LIMIT #{limit}
            """)
    List<GmUserRow> listUsers(@Param("cursor") String cursor, @Param("limit") int limit);

    @Select("""
            SELECT id, username, display_name, role, status, account_kind, version
            FROM fm_user
            WHERE id = #{id}
            """)
    GmUserRow findUserById(String id);

    @Select("""
            SELECT actor_id, idempotency_key, request_fingerprint, response_json, created_at_ms
            FROM fm_gm_action_receipt
            WHERE actor_id = #{actorId} AND idempotency_key = #{idempotencyKey}
            FOR UPDATE
            """)
    GmActionReceipt findReceiptForUpdate(
            @Param("actorId") String actorId,
            @Param("idempotencyKey") String idempotencyKey);

    @Insert("""
            INSERT INTO fm_gm_action_receipt (
              actor_id, idempotency_key, request_fingerprint, response_json, created_at_ms
            ) VALUES (
              #{actorId}, #{idempotencyKey}, #{requestFingerprint}, #{responseJson}, #{createdAtMs}
            )
            """)
    int insertReceipt(GmActionReceipt receipt);

    @Insert("""
            INSERT INTO fm_gm_audit_event (
              id, actor_id, action, target, scope, request_id, reason, outcome, occurred_at_ms
            ) VALUES (
              #{id}, #{actorId}, #{action}, #{target}, #{scope}, #{requestId}, #{reason},
              #{outcome}, #{occurredAtMs}
            )
            """)
    int insertAudit(GmAuditEventRow event);

    @Select("""
            SELECT id, actor_id, action, target, scope, request_id, reason, outcome, occurred_at_ms
            FROM fm_gm_audit_event
            ORDER BY occurred_at_ms DESC, id DESC
            LIMIT #{limit}
            """)
    List<GmAuditEventRow> listAudit(@Param("limit") int limit);
}
