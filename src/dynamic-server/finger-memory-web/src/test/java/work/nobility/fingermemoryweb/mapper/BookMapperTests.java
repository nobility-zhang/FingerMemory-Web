package work.nobility.fingermemoryweb.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import work.nobility.fingermemoryweb.entity.Book;
import work.nobility.fingermemoryweb.entity.BookCategory;
import work.nobility.fingermemoryweb.entity.User;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class BookMapperTests {
  @Autowired
  BookMapper bookMapper;
  @Autowired
  UserMapper userMapper;
  @Autowired
  BookCategoryMapper bookCategoryMapper;
  @Test
  public void insertOneBookTest() {
    User user = new User();
    user.setUserEmail("zhangsan@example.com");
    user.setUserPassword("123456");
    user.setUserName("zhangsan");
    user.setUserAvatarUrl("https://picsum.photos/50/50");
    userMapper.insertOneUser(user);

    BookCategory bookCategory = new BookCategory();
    bookCategory.setCategoryName("分类1");
    bookCategoryMapper.insertOneBookCategory(bookCategory);

    Book book1 = new Book();
    book1.setBookName("图书名");
    book1.setBookAuthor(user.getUserId());
    book1.setBookCategory(bookCategory.getCategoryId());
    book1.setBookBaidescription("图书描述图书描述图书描述图书描述");
    book1.setBookCoverUrl("https://picsum.photos/400/400");

    bookMapper.insertOneBook(book1);

    Book book2 = bookMapper.selectBookById((int) book1.getBookId());
    book2.setBookCreateDate(null);

    assertEquals(book1, book2);
    bookMapper.deleteBookById((int) book1.getBookId());
    bookCategoryMapper.deleteBookCategoryById((int) bookCategory.getCategoryId());
    userMapper.deleteUserById((int) user.getUserId());
  }
  @Test
  public void updateBookTest() {
    User user = new User();
    user.setUserEmail("zhangsan@example.com");
    user.setUserPassword("123456");
    user.setUserName("zhangsan");
    user.setUserAvatarUrl("https://picsum.photos/50/50");

    userMapper.insertOneUser(user);

    BookCategory bookCategory = new BookCategory();
    bookCategory.setCategoryName("分类1");
    bookCategoryMapper.insertOneBookCategory(bookCategory);

    Book book1 = new Book();
    book1.setBookName("图书名");
    book1.setBookAuthor(user.getUserId());
    book1.setBookCategory(bookCategory.getCategoryId());
    book1.setBookBaidescription("图书描述图书描述图书描述图书描述");
    book1.setBookCoverUrl("https://picsum.photos/400/400");

    bookMapper.insertOneBook(book1);

    book1.setBookName("update图书名");
    bookMapper.updateBook(book1);

    Book book2 = bookMapper.selectBookById((int) book1.getBookId());
    book2.setBookCreateDate(null);

    assertEquals(book1, book2);
    bookMapper.deleteBookById((int) book1.getBookId());
    bookCategoryMapper.deleteBookCategoryById((int) bookCategory.getCategoryId());
    userMapper.deleteUserById((int) user.getUserId());
  }
}
