package work.nobility.fingermemoryweb.mapper;
import org.apache.ibatis.annotations.*;
import work.nobility.fingermemoryweb.entity.Lexicon;

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
}
