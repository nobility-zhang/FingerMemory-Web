package work.nobility.fingermemoryweb.mapper;

import org.apache.ibatis.annotations.*;
import work.nobility.fingermemoryweb.entity.WordCategory;

@Mapper
public interface WordCategoryMapper {
  @Insert("insert into " +
      "fm_word_categorys(category_name)" +
      "value(#{categoryName})")
  @SelectKey(
      keyProperty = "categoryId",
      resultType = Long.class,
      before = false,
      statement = "select last_insert_id()"
  )
  Integer insertOneWordCategory(WordCategory wordCategory);

  @Update("update fm_word_categorys set " +
      "category_name = #{categoryName} " +
      "where category_id = #{categoryId}")
  Integer updateWordCategory(WordCategory wordCategory);

  @Select("select * from fm_word_categorys where category_id = #{id}")
  WordCategory selectWordCategoryById(Long id);

  @Delete("delete from fm_word_categorys where category_id = #{id}")
  Integer deleteWordCategoryById(Long id);

  @Select("select * from fm_word_categorys where category_name = #{name}")
  WordCategory selectWordCategoryByName(String name);
}
