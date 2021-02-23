package work.nobility.fingermemoryweb.mapper;

import org.apache.ibatis.annotations.*;
import work.nobility.fingermemoryweb.entity.User;

@Mapper
public interface UserMapper {
  @Insert("insert into " +
      "fm_users(user_name, user_email, user_password, user_create_date, user_avatar_url)" +
      "value(#{userName}, #{userEmail}, #{userPassword}, current_timestamp, #{userAvatarUrl})")
  @SelectKey(
      keyProperty = "userId",
      resultType = Integer.class,
      before = false,
      statement = "select last_insert_id()"
  )
  Integer insertOneUser(User user);

  @Update("update fm_users set " +
      "user_name = #{userName}, " +
      "user_email = #{userEmail}, " +
      "user_password = #{userPassword}, " +
      "user_avatar_url = #{userAvatarUrl}" +
      "where user_id = #{userId}")
  Integer updateUser(User user);

  @Select("select * from fm_users where user_id = #{id}")
  User selectUserById(Integer id);

  @Delete("delete from fm_users where user_id = #{id}")
  Integer deleteUserById(Integer id);
}
