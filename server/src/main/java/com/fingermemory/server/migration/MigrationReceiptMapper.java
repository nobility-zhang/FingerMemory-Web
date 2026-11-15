package com.fingermemory.server.migration;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MigrationReceiptMapper {
    @Select("""
            SELECT request_fingerprint, response_json
            FROM fm_migration_receipt
            WHERE owner_id = #{ownerId} AND batch_id = #{batchId}
            """)
    MigrationReceiptRow find(@Param("ownerId") String ownerId,
                             @Param("batchId") String batchId);

    @Select("""
            SELECT batch_id, request_fingerprint, response_json, created_at_ms
            FROM fm_migration_receipt
            WHERE owner_id = #{ownerId}
            ORDER BY created_at_ms ASC, batch_id ASC
            """)
    List<MigrationReceiptRow> listByOwner(@Param("ownerId") String ownerId);

    @Insert("""
            INSERT INTO fm_migration_receipt (
              owner_id, batch_id, request_fingerprint, response_json, created_at_ms
            ) VALUES (
              #{ownerId}, #{batchId}, #{fingerprint}, CAST(#{responseJson} AS JSON), #{createdAtMs}
            )
            """)
    int insert(@Param("ownerId") String ownerId,
               @Param("batchId") String batchId,
               @Param("fingerprint") String fingerprint,
               @Param("responseJson") String responseJson,
               @Param("createdAtMs") long createdAtMs);

    @Delete("""
            DELETE FROM fm_migration_receipt
            WHERE owner_id = #{ownerId}
            """)
    int deleteByOwner(@Param("ownerId") String ownerId);
}
