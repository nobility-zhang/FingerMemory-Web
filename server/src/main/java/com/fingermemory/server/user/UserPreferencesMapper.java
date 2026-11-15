package com.fingermemory.server.user;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserPreferencesMapper {
    @Select("""
            SELECT user_id, nickname, avatar_key, timezone, daily_goal, theme, sound_enabled,
                   shortcuts_json, created_at_ms, updated_at_ms, version
            FROM fm_user_preferences
            WHERE user_id = #{userId}
            """)
    UserPreferences findByUserId(String userId);

    @Insert("""
            INSERT INTO fm_user_preferences (
              user_id, nickname, avatar_key, timezone, daily_goal, theme, sound_enabled,
              shortcuts_json, created_at_ms, updated_at_ms, version
            ) VALUES (
              #{userId}, #{nickname}, #{avatarKey}, #{timezone}, #{dailyGoal}, #{theme}, #{soundEnabled},
              #{shortcutsJson}, #{createdAtMs}, #{updatedAtMs}, #{version}
            )
            """)
    int insert(UserPreferences preferences);
}
