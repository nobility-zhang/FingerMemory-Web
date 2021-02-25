package work.nobility.fingermemoryweb.mapper;
import org.apache.ibatis.annotations.*;
import work.nobility.fingermemoryweb.entity.BookCategory;

@Mapper
public interface BookCategoryMapper {
  @Insert("insert into " +
      "fm_book_categorys(category_name)" +
      "value(#{categoryName})")
  @SelectKey(
      keyProperty = "categoryId",
      resultType = Long.class,
      before = false,
      statement = "select last_insert_id()"
  )
  Integer insertOneBookCategory(BookCategory bookCategory);

  @Update("update fm_book_categorys set " +
      "category_name = #{categoryName} " +
      "where category_id = #{categoryId}")
  Integer updateBookCategory(BookCategory bookCategory);

  @Select("select * from fm_book_categorys where category_id = #{id}")
  BookCategory selectBookCategoryById(Long id);

  @Delete("delete from fm_book_categorys where category_id = #{id}")
  Integer deleteBookCategoryById(Long id);
}
