package work.nobility.fingermemoryweb.mapper;

import org.apache.ibatis.annotations.*;
import work.nobility.fingermemoryweb.entity.Book;

@Mapper
public interface BookMapper {
  @Insert("insert into " +
      "fm_books(book_name, book_author, book_category, book_baidescription, book_cover_url, book_create_date)" +
      "value(#{bookName}, #{bookAuthor}, #{bookCategory}, #{bookBaidescription}, #{bookCoverUrl}, current_timestamp)")
  @SelectKey(
      keyProperty = "bookId",
      resultType = Long.class,
      before = false,
      statement = "select last_insert_id()"
  )
  Integer insertOneBook(Book book);

  @Update("update fm_books set " +
      "book_name = #{bookName}, " +
      "book_author = #{bookAuthor}, " +
      "book_category = #{bookCategory}, " +
      "book_baidescription = #{bookBaidescription}, " +
      "book_cover_url = #{bookCoverUrl}" +
      "where book_id = #{bookId}")
  Integer updateBook(Book book);

  @Select("select * from fm_books where book_id = #{id}")
  Book selectBookById(Long id);

  @Delete("delete from fm_books where book_id = #{id}")
  Integer deleteBookById(Long id);
}
