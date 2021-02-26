package work.nobility.fingermemoryweb.mapper;

import org.apache.ibatis.annotations.*;
import work.nobility.fingermemoryweb.entity.Lexicon;
import work.nobility.fingermemoryweb.entity.Word;

import java.util.List;

@Mapper
public interface LexiconMapper {
  @Insert("insert into " +
      "fm_lexicons(lexicon_name, lexicon_author, lexicon_baidescription, lexicon_create_date, lexicon_cover_url)" +
      "value(#{lexiconName}, #{lexiconAuthor}, #{lexiconBaidescription}, current_timestamp, #{lexiconCoverUrl})")
  @SelectKey(
      keyProperty = "lexiconId",
      resultType = Long.class,
      before = false,
      statement = "select last_insert_id()"
  )
  Integer insertOneLexicon(Lexicon lexicon);

  @Update("update fm_lexicons set " +
      "lexicon_name = #{lexiconName}, " +
      "lexicon_author = #{lexiconAuthor}, " +
      "lexicon_baidescription = #{lexiconBaidescription}, " +
      "lexicon_cover_url = #{lexiconCoverUrl}" +
      "where lexicon_id = #{lexiconId}")
  Integer updateLexicon(Lexicon lexicon);

  @Select("select * from fm_lexicons where lexicon_id = #{id}")
  Lexicon selectLexiconById(Long id);

  @Delete("delete from fm_lexicons where lexicon_id = #{id}")
  Integer deleteLexiconById(Long id);

  @Select("select * from fm_lexicons where lexicon_author = #{id}")
  List<Lexicon> selectLexiconByAuthorId(Long id);

  @Select("select lexicon_id, lexicon_author, lexicon_name, lexicon_baidescription, lexicon_create_date, lexicon_cover_url " +
      "from fm_users as users " +
      "left join fm_user_lexicons as user_lexicons using(user_id) " +
      "left join fm_lexicons as lexicons using(lexicon_id) " +
      "where user_id = #{id} ")
  List<Lexicon> selectLexiconByUserLexicon(Long id);

  @Select("select word_id,word_english,word_category,word_cover_url,word_translate,word_baidescription " +
      "from fm_words as words " +
      "left join fm_lexicon_word_mapping as lexicon_word_mapping using(word_id) " +
      "left join fm_lexicons as lexicons using(lexicon_id) " +
      "where lexicon_id = #{id}")
  List<Word> selectLexiconByLexiconWord(Long id);

  @Select(
      "<script>" +
          "select * from fm_lexicons" +
          "<where>" +
          "<if test='name != null'> and lexicon_name = #{name}</if>" +
          "<if test='authorId != null'> and lexicon_author = #{authorId}</if>" +
          "<if test='prefix != null'> and lexicon_name like concat( #{prefix}, '%' )</if>" +
          "<if test='suffix != null'> and lexicon_name like concat( '%', #{suffix} )</if>" +
          "<if test='contain != null'> and lexicon_name like concat( '%', #{contain}, '%')</if>" +
          "</where>" +
          "</script>"
  )
  List<Lexicon> selectLexicon(@Param("name") String name,
                              @Param("authorId") Long authorId,
                              @Param("prefix") String prefix,
                              @Param("suffix") String suffix,
                              @Param("contain") String contain);
}
