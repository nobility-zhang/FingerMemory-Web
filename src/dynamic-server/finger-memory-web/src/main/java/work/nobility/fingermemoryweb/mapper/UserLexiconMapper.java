package work.nobility.fingermemoryweb.mapper;
import org.apache.ibatis.annotations.*;
import work.nobility.fingermemoryweb.entity.UserLexicon;

import java.util.List;

@Mapper
public interface UserLexiconMapper {
  @Insert("insert into " +
      "fm_user_lexicons(user_id, lexicon_id)" +
      "value(#{userId}, #{lexiconId})")
  Integer insertOneLexiconWordMapping(UserLexicon userLexicon);

  @Update("update fm_user_lexicons set " +
      "user_id = #{after.userId}, " +
      "lexicon_id = #{after.lexiconId} " +
      "where user_id = #{before.userId} and lexicon_id = #{before.lexiconId}")
  Integer updateLexiconWordMapping(@Param("before")UserLexicon userLexiconBefore,
                                   @Param("after")UserLexicon userLexiconAfter);

  @Select("select * from fm_user_lexicons where user_id = #{id}")
  List<UserLexicon> selectLexiconWordMappingListByUserId(Long id);

  @Select("select * from fm_user_lexicons where lexicon_id = #{id}")
  List<UserLexicon> selectLexiconWordMappingListByLexiconId(Long id);

  @Delete("delete from fm_user_lexicons where user_id = #{userId} and lexicon_id = #{lexiconId}")
  Integer deleteLexiconWordMappingByUserId(UserLexicon userLexicon);
}
