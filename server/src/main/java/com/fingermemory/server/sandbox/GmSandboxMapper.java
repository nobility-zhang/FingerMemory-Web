package com.fingermemory.server.sandbox;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface GmSandboxMapper {
    @Insert("""
            INSERT INTO fm_gm_sandbox (
              owner_admin_id, id, template_id, content_installation_id, version,
              data_epoch, state_json, created_at_ms, updated_at_ms
            ) VALUES (
              #{ownerAdminId}, #{id}, #{templateId}, #{contentInstallationId}, #{version},
              #{dataEpoch}, CAST(#{stateJson} AS JSON), #{createdAtMs}, #{updatedAtMs}
            )
            """)
    int insert(GmSandboxRow row);

    @Select("""
            SELECT owner_admin_id AS ownerAdminId, id, template_id AS templateId,
                   content_installation_id AS contentInstallationId, version,
                   data_epoch AS dataEpoch, state_json AS stateJson,
                   created_at_ms AS createdAtMs, updated_at_ms AS updatedAtMs
            FROM fm_gm_sandbox
            WHERE owner_admin_id = #{ownerAdminId} AND id = #{id}
            FOR UPDATE
            """)
    GmSandboxRow findForUpdate(
            @Param("ownerAdminId") String ownerAdminId,
            @Param("id") String id);

    @Select("""
            SELECT owner_admin_id AS ownerAdminId, id, template_id AS templateId,
                   content_installation_id AS contentInstallationId, version,
                   data_epoch AS dataEpoch, state_json AS stateJson,
                   created_at_ms AS createdAtMs, updated_at_ms AS updatedAtMs
            FROM fm_gm_sandbox
            WHERE owner_admin_id = #{ownerAdminId}
              AND (#{cursor} IS NULL OR id > #{cursor})
            ORDER BY id
            LIMIT #{limit}
            """)
    List<GmSandboxRow> listOwned(
            @Param("ownerAdminId") String ownerAdminId,
            @Param("cursor") String cursor,
            @Param("limit") int limit);

    @Update("""
            UPDATE fm_gm_sandbox
            SET version = #{nextVersion},
                data_epoch = #{dataEpoch},
                state_json = CAST(#{stateJson} AS JSON),
                updated_at_ms = #{updatedAtMs}
            WHERE owner_admin_id = #{ownerAdminId}
              AND id = #{id}
              AND version = #{expectedVersion}
            """)
    int updateState(
            @Param("ownerAdminId") String ownerAdminId,
            @Param("id") String id,
            @Param("expectedVersion") long expectedVersion,
            @Param("nextVersion") long nextVersion,
            @Param("dataEpoch") String dataEpoch,
            @Param("stateJson") String stateJson,
            @Param("updatedAtMs") long updatedAtMs);

    @Select("""
            SELECT owner_admin_id AS ownerAdminId, sandbox_id AS sandboxId,
                   data_epoch AS dataEpoch, idempotency_key AS idempotencyKey,
                   request_fingerprint AS requestFingerprint, response_json AS responseJson,
                   sandbox_version AS sandboxVersion, created_at_ms AS createdAtMs
            FROM fm_gm_sandbox_receipt
            WHERE owner_admin_id = #{ownerAdminId}
              AND sandbox_id = #{sandboxId}
              AND data_epoch = #{dataEpoch}
              AND idempotency_key = #{idempotencyKey}
            """)
    GmSandboxReceiptRow findReceipt(
            @Param("ownerAdminId") String ownerAdminId,
            @Param("sandboxId") String sandboxId,
            @Param("dataEpoch") String dataEpoch,
            @Param("idempotencyKey") String idempotencyKey);

    @Insert("""
            INSERT INTO fm_gm_sandbox_receipt (
              owner_admin_id, sandbox_id, data_epoch, idempotency_key,
              request_fingerprint, response_json, sandbox_version, created_at_ms
            ) VALUES (
              #{ownerAdminId}, #{sandboxId}, #{dataEpoch}, #{idempotencyKey},
              #{requestFingerprint}, CAST(#{responseJson} AS JSON), #{sandboxVersion}, #{createdAtMs}
            )
            """)
    int insertReceipt(GmSandboxReceiptRow row);
}
