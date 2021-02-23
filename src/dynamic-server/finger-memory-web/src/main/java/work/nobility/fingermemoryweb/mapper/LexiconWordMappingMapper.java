package work.nobility.fingermemoryweb.mapper;
import org.apache.ibatis.annotations.*;
import work.nobility.fingermemoryweb.entity.LexiconWordMapping;

import java.util.List;

@Mapper
public interface LexiconWordMappingMapper {
  @Insert("insert into " +
      "fm_lexicon_word_mapping(lexicon_id, word_id)" +
      "value(#{lexiconId}, #{wordId})")
  Integer insertOneLexiconWordMapping(LexiconWordMapping lexiconWordMapping);

  @Update("update fm_lexicon_word_mapping set " +
      "lexicon_id = #{after.lexiconId}, " +
      "word_id = #{after.wordId} " +
      "where lexicon_id = #{before.lexiconId} and word_id = #{before.wordId}")
  Integer updateLexiconWordMapping(@Param("before")LexiconWordMapping lexiconWordMappingBefore,
                                   @Param("after")LexiconWordMapping lexiconWordMappingAfter);

  @Select("select * from fm_lexicon_word_mapping where lexicon_id = #{id}")
  List<LexiconWordMapping> selectLexiconWordMappingListByLexiconId(Integer id);

  @Select("select * from fm_lexicon_word_mapping where word_id = #{id}")
  List<LexiconWordMapping> selectLexiconWordMappingListByWordId(Integer id);

  @Delete("delete from fm_lexicon_word_mapping where lexicon_id = #{lexiconId} and word_id = #{wordId}")
  Integer deleteLexiconWordMapping(LexiconWordMapping lexiconWordMapping);
}
