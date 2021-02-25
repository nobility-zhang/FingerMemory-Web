package work.nobility.fingermemoryweb.mapper;

import org.apache.ibatis.annotations.*;
import work.nobility.fingermemoryweb.entity.UserBookMapping;

import java.util.List;

@Mapper
public interface UserBookMappingMapper {
  @Insert("insert into " +
      "fm_user_book_mapping(user_id, book_id)" +
      "value(#{userId}, #{bookId})")
  Integer insertOneUserBookMapping(UserBookMapping userBookMapping);

  @Update("update fm_user_book_mapping set " +
      "user_id = #{after.userId}, " +
      "book_id = #{after.bookId} " +
      "where user_id = #{before.userId} and book_id = #{before.bookId}")
  Integer updateUserBookMapping(@Param("before") UserBookMapping userBookMappingBefore,
                                @Param("after") UserBookMapping userBookMappingAfter);

  @Select("select * from fm_user_book_mapping where user_id = #{id}")
  List<UserBookMapping> selectUserBookMappingListByUserId(Long id);

  @Select("select * from fm_user_book_mapping where book_id = #{id}")
  List<UserBookMapping> selectUserBookMappingListByBookId(Long id);

  @Delete("delete from fm_user_book_mapping where user_id = #{userId} and book_id = #{bookId}")
  Integer deleteUserBookMapping(UserBookMapping userBookMapping);
}
