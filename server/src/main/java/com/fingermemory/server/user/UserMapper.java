package com.fingermemory.server.user;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper {
    @Select("""
            SELECT id, username, username_normalized, display_name, password_hash, status,
                   created_at_ms, updated_at_ms, last_login_at_ms, version
            FROM fm_user
            WHERE username_normalized = #{usernameNormalized}
            """)
    UserAccount findByNormalizedUsername(String usernameNormalized);

    @Select("""
            SELECT id, username, username_normalized, display_name, password_hash, status,
                   created_at_ms, updated_at_ms, last_login_at_ms, version
            FROM fm_user
            WHERE id = #{id}
            """)
    UserAccount findById(String id);

    @Select("""
            SELECT status
            FROM fm_user
            WHERE id = #{id}
            """)
    String findStatusById(String id);

    @Select("""
            SELECT id, username, username_normalized, display_name, password_hash, status,
                   created_at_ms, updated_at_ms, last_login_at_ms, version
            FROM fm_user
            ORDER BY username_normalized ASC
            """)
    List<UserAccount> listAll();

    @Insert("""
            INSERT INTO fm_user (
              id, username, username_normalized, display_name, password_hash, status,
              created_at_ms, updated_at_ms, last_login_at_ms, version
            ) VALUES (
              #{id}, #{username}, #{usernameNormalized}, #{displayName}, #{passwordHash}, #{status},
              #{createdAtMs}, #{updatedAtMs}, #{lastLoginAtMs}, #{version}
            )
            """)
    int insert(UserAccount user);

    @Update("""
            UPDATE fm_user
            SET last_login_at_ms = #{atMs}, updated_at_ms = #{atMs}
            WHERE id = #{id} AND status = 'active'
            """)
    int touchLastLogin(@Param("id") String id, @Param("atMs") long atMs);

    @Update("""
            UPDATE fm_user
            SET password_hash = #{passwordHash}, updated_at_ms = #{atMs}, version = version + 1
            WHERE id = #{id} AND status = 'active'
            """)
    int updatePassword(@Param("id") String id, @Param("passwordHash") String passwordHash,
                       @Param("atMs") long atMs);

    @Update("""
            UPDATE fm_user
            SET status = #{status}, updated_at_ms = #{atMs}, version = version + 1
            WHERE id = #{id}
            """)
    int updateStatus(@Param("id") String id, @Param("status") String status,
                     @Param("atMs") long atMs);
}
