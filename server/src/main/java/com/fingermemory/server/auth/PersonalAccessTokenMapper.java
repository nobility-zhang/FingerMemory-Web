package com.fingermemory.server.auth;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PersonalAccessTokenMapper {
    @Insert("""
            INSERT INTO fm_personal_access_token (
              id, owner_id, name, token_hash, token_prefix, scope,
              created_at_ms, expires_at_ms, last_used_at_ms, revoked_at_ms, version
            ) VALUES (
              #{id}, #{ownerId}, #{name}, #{tokenHash}, #{tokenPrefix}, #{scope},
              #{createdAtMs}, #{expiresAtMs}, #{lastUsedAtMs}, #{revokedAtMs}, #{version}
            )
            """)
    int insert(PersonalAccessToken token);

    @Select("""
            SELECT id, owner_id, name, token_hash, token_prefix, scope,
                   created_at_ms, expires_at_ms, last_used_at_ms, revoked_at_ms, version
            FROM fm_personal_access_token
            WHERE owner_id = #{ownerId}
            ORDER BY created_at_ms DESC, id DESC
            """)
    List<PersonalAccessToken> listByOwner(String ownerId);

    @Select("""
            SELECT id, owner_id, name, token_hash, token_prefix, scope,
                   created_at_ms, expires_at_ms, last_used_at_ms, revoked_at_ms, version
            FROM fm_personal_access_token
            WHERE token_hash = #{tokenHash}
            """)
    PersonalAccessToken findByHash(String tokenHash);

    @Select("""
            SELECT id, owner_id, name, token_hash, token_prefix, scope,
                   created_at_ms, expires_at_ms, last_used_at_ms, revoked_at_ms, version
            FROM fm_personal_access_token
            WHERE owner_id = #{ownerId} AND id = #{id}
            """)
    PersonalAccessToken findByOwnerAndId(
            @Param("ownerId") String ownerId,
            @Param("id") String id);

    @Update("""
            UPDATE fm_personal_access_token
            SET last_used_at_ms = #{atMs}
            WHERE id = #{id} AND revoked_at_ms IS NULL
            """)
    int touchLastUsed(@Param("id") String id, @Param("atMs") long atMs);

    @Update("""
            UPDATE fm_personal_access_token
            SET revoked_at_ms = #{atMs}, version = version + 1
            WHERE id = #{id} AND owner_id = #{ownerId} AND revoked_at_ms IS NULL
            """)
    int revoke(@Param("id") String id, @Param("ownerId") String ownerId,
               @Param("atMs") long atMs);

    @Update("""
            UPDATE fm_personal_access_token
            SET revoked_at_ms = #{atMs}, version = version + 1
            WHERE owner_id = #{ownerId} AND revoked_at_ms IS NULL
            """)
    int revokeAll(@Param("ownerId") String ownerId, @Param("atMs") long atMs);
}
