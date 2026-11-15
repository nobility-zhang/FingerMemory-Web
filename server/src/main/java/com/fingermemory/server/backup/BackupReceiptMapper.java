package com.fingermemory.server.backup;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BackupReceiptMapper {
    @Select("""
            SELECT request_fingerprint, response_json
            FROM fm_backup_receipt
            WHERE owner_id = #{ownerId} AND idempotency_key = #{idempotencyKey}
            """)
    BackupReceiptRow find(@Param("ownerId") String ownerId,
                          @Param("idempotencyKey") String idempotencyKey);

    @Insert("""
            INSERT INTO fm_backup_receipt (
              owner_id, idempotency_key, request_fingerprint, response_json, created_at_ms
            ) VALUES (
              #{ownerId}, #{idempotencyKey}, #{fingerprint},
              CAST(#{responseJson} AS JSON), #{createdAtMs}
            )
            """)
    int insert(@Param("ownerId") String ownerId,
               @Param("idempotencyKey") String idempotencyKey,
               @Param("fingerprint") String fingerprint,
               @Param("responseJson") String responseJson,
               @Param("createdAtMs") long createdAtMs);

    @Delete("""
            DELETE FROM fm_backup_receipt
            WHERE owner_id = #{ownerId}
            """)
    int deleteByOwner(@Param("ownerId") String ownerId);
}
