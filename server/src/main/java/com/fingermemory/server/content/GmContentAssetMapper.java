package com.fingermemory.server.content;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GmContentAssetMapper {
    @Insert("""
            INSERT INTO fm_gm_content_asset (
              content_id, asset_path, content_hash, mime, byte_size, kind, license
            ) VALUES (
              #{contentId}, #{assetPath}, #{contentHash}, #{mime}, #{byteSize}, #{kind}, #{license}
            )
            """)
    int insert(GmContentAssetRow row);

    @Select("""
            SELECT content_id, asset_path, content_hash, mime, byte_size, kind, license
            FROM fm_gm_content_asset
            WHERE content_id = #{contentId}
            ORDER BY asset_path
            """)
    List<GmContentAssetRow> list(@Param("contentId") String contentId);

    @Select("""
            SELECT content_id, asset_path, content_hash, mime, byte_size, kind, license
            FROM fm_gm_content_asset
            WHERE content_id = #{contentId}
              AND asset_path = #{assetPath}
            """)
    GmContentAssetRow find(
            @Param("contentId") String contentId,
            @Param("assetPath") String assetPath);
}
