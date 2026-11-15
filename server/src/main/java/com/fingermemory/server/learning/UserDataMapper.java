package com.fingermemory.server.learning;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserDataMapper {
    @Insert("""
            INSERT IGNORE INTO fm_user_data (
              owner_id, data_version, schema_version, data_format_version, payload_json,
              created_at_ms, updated_at_ms
            ) VALUES (
              #{ownerId}, 0, 4, 4, CAST(#{payloadJson} AS JSON), #{atMs}, #{atMs}
            )
            """)
    int insertDefault(@Param("ownerId") String ownerId, @Param("payloadJson") String payloadJson,
                      @Param("atMs") long atMs);

    @Select("""
            SELECT owner_id, data_version, schema_version, data_format_version, payload_json,
                   created_at_ms, updated_at_ms
            FROM fm_user_data
            WHERE owner_id = #{ownerId}
            """)
    UserDataRow find(@Param("ownerId") String ownerId);

    @Select("""
            SELECT owner_id, data_version, schema_version, data_format_version, payload_json,
                   created_at_ms, updated_at_ms
            FROM fm_user_data
            WHERE owner_id = #{ownerId}
            FOR UPDATE
            """)
    UserDataRow findForUpdate(@Param("ownerId") String ownerId);

    @Update("""
            UPDATE fm_user_data
            SET data_version = #{nextVersion},
                payload_json = CAST(#{payloadJson} AS JSON),
                updated_at_ms = #{atMs}
            WHERE owner_id = #{ownerId} AND data_version = #{expectedVersion}
            """)
    int update(@Param("ownerId") String ownerId,
               @Param("expectedVersion") long expectedVersion,
               @Param("nextVersion") long nextVersion,
               @Param("payloadJson") String payloadJson,
               @Param("atMs") long atMs);
}
