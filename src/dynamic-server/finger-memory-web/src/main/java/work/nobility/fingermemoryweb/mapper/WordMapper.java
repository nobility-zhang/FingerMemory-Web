package work.nobility.fingermemoryweb.mapper;
import org.apache.ibatis.annotations.*;
import work.nobility.fingermemoryweb.entity.Word;

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
}
