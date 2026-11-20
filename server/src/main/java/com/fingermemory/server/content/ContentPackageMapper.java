package com.fingermemory.server.content;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ContentPackageMapper {
    @Select("""
            SELECT owner_id, id, catalog_content_id, namespace_id, book_id,
                   content_version, content_hash,
                   title, entry_node_id, engine_version, schema_version,
                   manifest_json, storage_relative_path, word_count, chapter_count,
                   node_count, character_count, card_count, encounter_count,
                   asset_count, status, installed_at_ms, updated_at_ms
            FROM fm_content_installation
            WHERE owner_id = #{ownerId} AND content_hash = #{contentHash}
            """)
    ContentInstallationRow findByHash(
            @Param("ownerId") String ownerId,
            @Param("contentHash") String contentHash);

    @Select("""
            SELECT owner_id, id, catalog_content_id, namespace_id, book_id,
                   content_version, content_hash,
                   title, entry_node_id, engine_version, schema_version,
                   manifest_json, storage_relative_path, word_count, chapter_count,
                   node_count, character_count, card_count, encounter_count,
                   asset_count, status, installed_at_ms, updated_at_ms
            FROM fm_content_installation
            WHERE owner_id = #{ownerId}
              AND namespace_id = #{namespaceId}
              AND book_id = #{bookId}
              AND content_version = #{contentVersion}
            """)
    ContentInstallationRow findByVersion(
            @Param("ownerId") String ownerId,
            @Param("namespaceId") String namespaceId,
            @Param("bookId") String bookId,
            @Param("contentVersion") String contentVersion);

    @Select("""
            SELECT owner_id, id, catalog_content_id, namespace_id, book_id,
                   content_version, content_hash,
                   title, entry_node_id, engine_version, schema_version,
                   manifest_json, storage_relative_path, word_count, chapter_count,
                   node_count, character_count, card_count, encounter_count,
                   asset_count, status, installed_at_ms, updated_at_ms
            FROM fm_content_installation
            WHERE owner_id = #{ownerId} AND id = #{id}
            """)
    ContentInstallationRow findById(
            @Param("ownerId") String ownerId,
            @Param("id") String id);

    @Select("""
            SELECT owner_id, id, catalog_content_id, namespace_id, book_id,
                   content_version, content_hash,
                   title, entry_node_id, engine_version, schema_version,
                   manifest_json, storage_relative_path, word_count, chapter_count,
                   node_count, character_count, card_count, encounter_count,
                   asset_count, status, installed_at_ms, updated_at_ms
            FROM fm_content_installation
            WHERE owner_id = #{ownerId}
            ORDER BY installed_at_ms DESC, id
            LIMIT #{limit}
            """)
    List<ContentInstallationRow> listOwned(
            @Param("ownerId") String ownerId,
            @Param("limit") int limit);

    @Insert("""
            INSERT INTO fm_content_installation (
              owner_id, id, catalog_content_id, namespace_id, book_id,
              content_version, content_hash,
              title, entry_node_id, engine_version, schema_version, manifest_json,
              storage_relative_path, word_count, chapter_count, node_count,
              character_count, card_count, encounter_count, asset_count, status,
              installed_at_ms, updated_at_ms
            ) VALUES (
              #{ownerId}, #{id}, #{catalogContentId}, #{namespaceId}, #{bookId},
              #{contentVersion}, #{contentHash}, #{title}, #{entryNodeId}, #{engineVersion},
              #{schemaVersion}, CAST(#{manifestJson} AS JSON),
              #{storageRelativePath}, #{wordCount}, #{chapterCount}, #{nodeCount},
              #{characterCount}, #{cardCount}, #{encounterCount}, #{assetCount},
              #{status}, #{installedAtMs}, #{updatedAtMs}
            )
            """)
    int insert(ContentInstallationRow row);

    @Update("""
            UPDATE fm_content_installation
            SET catalog_content_id = #{catalogContentId}
            WHERE owner_id = #{ownerId}
              AND id = #{installationId}
              AND catalog_content_id IS NULL
            """)
    int linkCatalogContent(
            @Param("ownerId") String ownerId,
            @Param("installationId") String installationId,
            @Param("catalogContentId") String catalogContentId);

    @Insert("""
            INSERT INTO fm_content_asset (
              owner_id, installation_id, asset_path, content_hash, mime,
              byte_size, asset_kind, license_text, created_at_ms
            ) VALUES (
              #{ownerId}, #{installationId}, #{assetPath}, #{contentHash},
              #{mime}, #{byteSize}, #{assetKind}, #{licenseText}, #{createdAtMs}
            )
            """)
    int insertAsset(ContentAssetRow row);

    @Select("""
            SELECT owner_id, installation_id, asset_path, content_hash, mime,
                   byte_size, asset_kind, license_text, created_at_ms
            FROM fm_content_asset
            WHERE owner_id = #{ownerId}
              AND installation_id = #{installationId}
              AND asset_path = #{assetPath}
            """)
    ContentAssetRow findAsset(
            @Param("ownerId") String ownerId,
            @Param("installationId") String installationId,
            @Param("assetPath") String assetPath);

    @Select("""
            SELECT owner_id, idempotency_key, request_fingerprint, installation_id,
                   response_json, created_at_ms
            FROM fm_content_installation_receipt
            WHERE owner_id = #{ownerId} AND idempotency_key = #{idempotencyKey}
            """)
    ContentInstallationReceiptRow findReceipt(
            @Param("ownerId") String ownerId,
            @Param("idempotencyKey") String idempotencyKey);

    @Insert("""
            INSERT INTO fm_content_installation_receipt (
              owner_id, idempotency_key, request_fingerprint,
              installation_id, response_json, created_at_ms
            ) VALUES (
              #{ownerId}, #{idempotencyKey}, #{requestFingerprint},
              #{installationId}, CAST(#{responseJson} AS JSON), #{createdAtMs}
            )
            """)
    int insertReceipt(ContentInstallationReceiptRow row);
}
