package com.fingermemory.server.lexicon;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface LexiconMapper {
    @Select("""
            SELECT id, owner_id, import_batch_id, name, normalized_name, description, source, archived_at_ms,
                   created_at_ms, updated_at_ms, version
            FROM fm_lexicon
            WHERE owner_id = #{ownerId} AND archived_at_ms IS NULL
            ORDER BY updated_at_ms DESC, id ASC
            LIMIT #{limit}
            """)
    List<Lexicon> listActive(@Param("ownerId") String ownerId, @Param("limit") int limit);

    @Select("""
            SELECT id, owner_id, import_batch_id, name, normalized_name, description, source, archived_at_ms,
                   created_at_ms, updated_at_ms, version
            FROM fm_lexicon
            WHERE owner_id = #{ownerId}
            ORDER BY updated_at_ms DESC, id ASC
            LIMIT #{limit}
            """)
    List<Lexicon> listIncludingArchived(@Param("ownerId") String ownerId, @Param("limit") int limit);

    @Select("""
            SELECT id, owner_id, import_batch_id, name, normalized_name, description, source,
                   archived_at_ms, created_at_ms, updated_at_ms, version
            FROM fm_lexicon
            WHERE owner_id = #{ownerId}
            ORDER BY created_at_ms ASC, id ASC
            """)
    List<Lexicon> listAllByOwner(@Param("ownerId") String ownerId);

    @Select("""
            SELECT id, owner_id, import_batch_id, name, normalized_name, description, source, archived_at_ms,
                   created_at_ms, updated_at_ms, version
            FROM fm_lexicon
            WHERE id = #{id} AND owner_id = #{ownerId}
            """)
    Lexicon findByIdAndOwner(@Param("id") String id, @Param("ownerId") String ownerId);

    @Insert("""
            INSERT INTO fm_lexicon (
              id, owner_id, import_batch_id, name, normalized_name, description, source, archived_at_ms,
              created_at_ms, updated_at_ms, version
            ) VALUES (
              #{id}, #{ownerId}, #{importBatchId}, #{name}, #{normalizedName}, #{description}, #{source}, #{archivedAtMs},
              #{createdAtMs}, #{updatedAtMs}, #{version}
            )
            """)
    int insert(Lexicon lexicon);

    @Update("""
            UPDATE fm_lexicon
            SET name = #{name}, normalized_name = #{normalizedName}, description = #{description},
                updated_at_ms = #{updatedAtMs}, version = version + 1
            WHERE id = #{id} AND owner_id = #{ownerId} AND version = #{version}
            """)
    int update(Lexicon lexicon);

    @Update("""
            UPDATE fm_lexicon
            SET archived_at_ms = #{archivedAtMs}, updated_at_ms = #{updatedAtMs}, version = version + 1
            WHERE id = #{id} AND owner_id = #{ownerId} AND version = #{version}
            """)
    int updateArchive(Lexicon lexicon);

    @Update("""
            DELETE FROM fm_lexicon
            WHERE owner_id = #{ownerId}
            """)
    int deleteAllByOwner(@Param("ownerId") String ownerId);
}
