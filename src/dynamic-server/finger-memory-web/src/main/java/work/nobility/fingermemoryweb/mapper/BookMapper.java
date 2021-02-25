package work.nobility.fingermemoryweb.mapper;

import org.apache.ibatis.annotations.*;
import work.nobility.fingermemoryweb.entity.Book;

import java.util.List;

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

  @Select(
      "<script>" +
          "select * from fm_books" +
          "<where>" +
          "<if test='bookName != null'> and book_name = #{bookName}</if>" +
          "<if test='bookCategoryId != null'> and book_categoryId = #{bookCategoryId}</if>" +
          "<if test='prefix != null'> and book_name like concat( #{prefix}, '%' )</if>" +
          "<if test='suffix != null'> and book_name like concat( '%', #{suffix} )</if>" +
          "<if test='contain != null'> and book_name like concat( '%', #{contain}, '%')</if>" +
          "</where>" +
          "</script>"
  )
  List<Book> selectBook(@Param("bookName") String bookName,
                        @Param("bookCategoryId") Long bookCategoryId,
                        @Param("prefix") String prefix,
                        @Param("suffix") String suffix,
                        @Param("contain") String contain);

  @Select("select book_id,book_name,book_author,book_category,book_baidescription,book_cover_url,book_create_date " +
      "from fm_users as users " +
      "left join fm_user_book_mapping as user_book_mapping using(user_id) " +
      "left join fm_books as books using(book_id)" +
      "where user_id = #{id}")
  List<Book> selectLexiconByUserLexicon(Long id);
}
