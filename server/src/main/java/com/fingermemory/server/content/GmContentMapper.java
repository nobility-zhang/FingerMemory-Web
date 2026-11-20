package com.fingermemory.server.content;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface GmContentMapper {
    @Select("""
            SELECT id, created_by, namespace_id, book_id, content_version,
                   content_hash, title, series_id, entry_node_id, manifest_json,
                   storage_relative_path, word_count, chapter_count, node_count,
                   character_count, card_count, encounter_count, asset_count,
                   status, issues_json, version, published_by, archived_by,
                   created_at_ms, updated_at_ms, published_at_ms, archived_at_ms
            FROM fm_gm_content
            WHERE id = #{id}
            """)
    GmContentRow findById(@Param("id") String id);

    @Select("""
            SELECT id, created_by, namespace_id, book_id, content_version,
                   content_hash, title, series_id, entry_node_id, manifest_json,
                   storage_relative_path, word_count, chapter_count, node_count,
                   character_count, card_count, encounter_count, asset_count,
                   status, issues_json, version, published_by, archived_by,
                   created_at_ms, updated_at_ms, published_at_ms, archived_at_ms
            FROM fm_gm_content
            WHERE id = #{id}
            FOR UPDATE
            """)
    GmContentRow findForUpdate(@Param("id") String id);

    @Select("""
            SELECT id, created_by, namespace_id, book_id, content_version,
                   content_hash, title, series_id, entry_node_id, manifest_json,
                   storage_relative_path, word_count, chapter_count, node_count,
                   character_count, card_count, encounter_count, asset_count,
                   status, issues_json, version, published_by, archived_by,
                   created_at_ms, updated_at_ms, published_at_ms, archived_at_ms
            FROM fm_gm_content
            WHERE content_hash = #{contentHash}
            """)
    GmContentRow findByHash(@Param("contentHash") String contentHash);

    @Select("""
            SELECT id, created_by, namespace_id, book_id, content_version,
                   content_hash, title, series_id, entry_node_id, manifest_json,
                   storage_relative_path, word_count, chapter_count, node_count,
                   character_count, card_count, encounter_count, asset_count,
                   status, issues_json, version, published_by, archived_by,
                   created_at_ms, updated_at_ms, published_at_ms, archived_at_ms
            FROM fm_gm_content
            WHERE namespace_id = #{namespaceId}
              AND book_id = #{bookId}
              AND content_version = #{contentVersion}
            """)
    GmContentRow findByVersion(
            @Param("namespaceId") String namespaceId,
            @Param("bookId") String bookId,
            @Param("contentVersion") String contentVersion);

    @Select("""
            SELECT id, created_by, namespace_id, book_id, content_version,
                   content_hash, title, series_id, entry_node_id, manifest_json,
                   storage_relative_path, word_count, chapter_count, node_count,
                   character_count, card_count, encounter_count, asset_count,
                   status, issues_json, version, published_by, archived_by,
                   created_at_ms, updated_at_ms, published_at_ms, archived_at_ms
            FROM fm_gm_content
            WHERE (status IN ('published', 'archived') OR created_by = #{actorId})
              AND (#{cursor} IS NULL OR id < #{cursor})
            ORDER BY id DESC
            LIMIT #{limit}
            """)
    List<GmContentRow> listVisible(
            @Param("actorId") String actorId,
            @Param("cursor") String cursor,
            @Param("limit") int limit);

    @Select("""
            SELECT id, created_by, namespace_id, book_id, content_version,
                   content_hash, title, series_id, entry_node_id, manifest_json,
                   storage_relative_path, word_count, chapter_count, node_count,
                   character_count, card_count, encounter_count, asset_count,
                   status, issues_json, version, published_by, archived_by,
                   created_at_ms, updated_at_ms, published_at_ms, archived_at_ms
            FROM fm_gm_content
            WHERE status = 'published'
            ORDER BY published_at_ms DESC, id ASC
            LIMIT #{limit}
            """)
    List<GmContentRow> listPublished(@Param("limit") int limit);

    @Insert("""
            INSERT INTO fm_gm_content (
              id, created_by, namespace_id, book_id, content_version,
              content_hash, title, series_id, entry_node_id, manifest_json,
              storage_relative_path, word_count, chapter_count, node_count,
              character_count, card_count, encounter_count, asset_count,
              status, issues_json, version, published_by, archived_by,
              created_at_ms, updated_at_ms, published_at_ms, archived_at_ms
            ) VALUES (
              #{row.id}, #{row.createdBy}, #{row.namespaceId}, #{row.bookId},
              #{row.contentVersion}, #{row.contentHash}, #{row.title},
              #{row.seriesId}, #{row.entryNodeId},
              CAST(#{row.manifestJson} AS JSON), #{row.storageRelativePath},
              #{row.wordCount}, #{row.chapterCount}, #{row.nodeCount},
              #{row.characterCount}, #{row.cardCount}, #{row.encounterCount},
              #{row.assetCount}, #{row.status}, CAST(#{row.issuesJson} AS JSON),
              #{row.version}, #{row.publishedBy}, #{row.archivedBy},
              #{row.createdAtMs}, #{row.updatedAtMs}, #{row.publishedAtMs},
              #{row.archivedAtMs}
            )
            """)
    int insert(@Param("row") GmContentRow row);

    @Update("""
            UPDATE fm_gm_content
            SET status = 'validated',
                issues_json = CAST(#{issuesJson} AS JSON),
                version = version + 1,
                updated_at_ms = #{updatedAtMs}
            WHERE id = #{id}
              AND content_hash = #{contentHash}
              AND status = 'draft'
            """)
    int markValidated(
            @Param("id") String id,
            @Param("contentHash") String contentHash,
            @Param("issuesJson") String issuesJson,
            @Param("updatedAtMs") long updatedAtMs);

    @Update("""
            UPDATE fm_gm_content
            SET status = 'published',
                published_by = #{actorId},
                published_at_ms = #{updatedAtMs},
                version = version + 1,
                updated_at_ms = #{updatedAtMs}
            WHERE id = #{id}
              AND content_hash = #{contentHash}
              AND status = 'validated'
            """)
    int publish(
            @Param("id") String id,
            @Param("contentHash") String contentHash,
            @Param("actorId") String actorId,
            @Param("updatedAtMs") long updatedAtMs);

    @Update("""
            UPDATE fm_gm_content
            SET status = 'archived',
                archived_by = #{actorId},
                archived_at_ms = #{updatedAtMs},
                version = version + 1,
                updated_at_ms = #{updatedAtMs}
            WHERE id = #{id}
              AND content_hash = #{contentHash}
              AND status = 'published'
            """)
    int archive(
            @Param("id") String id,
            @Param("contentHash") String contentHash,
            @Param("actorId") String actorId,
            @Param("updatedAtMs") long updatedAtMs);
}
