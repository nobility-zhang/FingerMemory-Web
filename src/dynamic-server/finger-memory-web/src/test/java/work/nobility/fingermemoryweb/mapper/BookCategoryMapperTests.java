package work.nobility.fingermemoryweb.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import work.nobility.fingermemoryweb.entity.BookCategory;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class BookCategoryMapperTests {
  @Autowired
  BookCategoryMapper bookCategoryMapper;

  @Test
  public void insertOneBookCategoryTest() {
    BookCategory bookCategory1 = new BookCategory();
    bookCategory1.setCategoryName("分类名");

    bookCategoryMapper.insertOneBookCategory(bookCategory1);

    BookCategory bookCategory2 = bookCategoryMapper.selectBookCategoryById((int) bookCategory1.getCategoryId());

    assertEquals(bookCategory1, bookCategory2);

    bookCategoryMapper.deleteBookCategoryById((int) bookCategory1.getCategoryId());
  }

  @Test
  public void updateBookCategoryTest() {
    BookCategory bookCategory1 = new BookCategory();
    bookCategory1.setCategoryName("分类名");

    bookCategoryMapper.insertOneBookCategory(bookCategory1);

    bookCategory1.setCategoryName("分类名update");
    bookCategoryMapper.updateBookCategory(bookCategory1);

    BookCategory bookCategory2 = bookCategoryMapper.selectBookCategoryById((int) bookCategory1.getCategoryId());

    assertEquals(bookCategory1, bookCategory2);

    bookCategoryMapper.deleteBookCategoryById((int) bookCategory1.getCategoryId());
  }
}
