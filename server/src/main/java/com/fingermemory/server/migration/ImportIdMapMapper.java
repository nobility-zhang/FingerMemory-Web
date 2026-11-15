package com.fingermemory.server.migration;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ImportIdMapMapper {
    @Insert("""
            INSERT INTO fm_import_id_map (
              owner_id, batch_id, entity_type, source_id, target_id
            ) VALUES (
              #{ownerId}, #{batchId}, #{entityType}, #{sourceId}, #{targetId}
            )
            """)
    int insert(@Param("ownerId") String ownerId,
               @Param("batchId") String batchId,
               @Param("entityType") String entityType,
               @Param("sourceId") String sourceId,
               @Param("targetId") String targetId);

    @Select("""
            SELECT COUNT(*)
            FROM fm_import_id_map
            WHERE owner_id = #{ownerId} AND batch_id = #{batchId}
            """)
    int countByBatch(@Param("ownerId") String ownerId, @Param("batchId") String batchId);

    @Select("""
            SELECT batch_id, entity_type, source_id, target_id
            FROM fm_import_id_map
            WHERE owner_id = #{ownerId}
            ORDER BY batch_id ASC, entity_type ASC, source_id ASC
            """)
    List<ImportIdMapRow> listByOwner(@Param("ownerId") String ownerId);

    @Delete("""
            DELETE FROM fm_import_id_map
            WHERE owner_id = #{ownerId}
            """)
    int deleteByOwner(@Param("ownerId") String ownerId);
}
