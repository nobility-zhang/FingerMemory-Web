package work.nobility.fingermemoryweb.mapper;

import org.apache.ibatis.annotations.*;
import work.nobility.fingermemoryweb.entity.Word;

import java.util.List;

@Mapper
public interface WordMapper {
  @Insert("insert into " +
      "fm_words(word_english, word_translate, word_baidescription, word_cover_url, word_category)" +
      "value(#{wordEnglish}, #{wordTranslate}, #{wordBaidescription}, #{wordCoverUrl}, #{wordCategory})")
  @SelectKey(
      keyProperty = "wordId",
      resultType = Long.class,
      before = false,
      statement = "select last_insert_id()"
  )
  Integer insertOneWord(Word word);

  @Update("update fm_words set " +
      "word_english = #{wordEnglish}, " +
      "word_translate = #{wordTranslate}, " +
      "word_baidescription = #{wordBaidescription}, " +
      "word_cover_url = #{wordCoverUrl}, " +
      "word_category = #{wordCategory}")
  Integer updateWord(Word word);

  @Select("select * from fm_words where word_id = #{id}")
  Word selectWordById(Long id);

  @Delete("delete from fm_words where word_id = #{id}")
  Integer deleteWordById(Long id);

  @Select(
      "<script>" +
          "select * from fm_words" +
          "<where>" +
          "<if test='english != null'> and word_english = #{english}</if>" +
          "<if test='wordCategoryId != null'> and word_category = #{wordCategoryId}</if>" +
          "<if test='prefix != null'> and word_english like concat( #{prefix}, '%' )</if>" +
          "<if test='suffix != null'> and word_english like concat( '%', #{suffix} )</if>" +
          "<if test='contain != null'> and word_english like concat( '%', #{contain}, '%')</if>" +
          "</where>" +
          "</script>"
  )
  List<Word> selectWord(@Param("english") String english,
                        @Param("wordCategoryId") Long wordCategoryId,
                        @Param("prefix") String prefix,
                        @Param("suffix") String suffix,
                        @Param("contain") String contain);

  @Select("select * from fm_words order by rand() limit 0,5")
  List<Word> selectRandomWord();
}
