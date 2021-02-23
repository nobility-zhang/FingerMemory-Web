package work.nobility.fingermemoryweb.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import work.nobility.fingermemoryweb.entity.Book;
import work.nobility.fingermemoryweb.entity.BookCategory;
import work.nobility.fingermemoryweb.entity.User;
import work.nobility.fingermemoryweb.entity.UserBookMapping;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class UserBookMappingMapperTests {
  @Autowired
  UserBookMappingMapper userBookMappingMapper;
  @Autowired
  UserMapper userMapper;
  @Autowired
  BookCategoryMapper bookCategoryMapper;
  @Autowired
  BookMapper bookMapper;

  @Test
  public void insertOneUserBookMappingTest() {
    User user1 = new User();
    user1.setUserEmail("zhangsan1@example.com");
    user1.setUserPassword("123456");
    user1.setUserName("zhangsan1");
    user1.setUserAvatarUrl("https://picsum.photos/50/50");
    userMapper.insertOneUser(user1);
    User user2 = new User();
    user2.setUserEmail("zhangsan2@example.com");
    user2.setUserPassword("123456");
    user2.setUserName("zhangsan2");
    user2.setUserAvatarUrl("https://picsum.photos/50/50");
    userMapper.insertOneUser(user2);

    BookCategory bookCategory = new BookCategory();
    bookCategory.setCategoryName("分类1");
    bookCategoryMapper.insertOneBookCategory(bookCategory);
    Book book = new Book();
    book.setBookName("图书名");
    book.setBookAuthor(user1.getUserId());
    book.setBookCategory(bookCategory.getCategoryId());
    book.setBookBaidescription("图书描述图书描述图书描述图书描述");
    book.setBookCoverUrl("https://picsum.photos/400/400");
    bookMapper.insertOneBook(book);

    UserBookMapping userBookMapping1 = new UserBookMapping();
    userBookMapping1.setBookId(book.getBookId());
    userBookMapping1.setUserId(user2.getUserId());
    userBookMappingMapper.insertOneUserBookMapping(userBookMapping1);

    UserBookMapping userBookMapping2 = userBookMappingMapper
        .selectUserBookMappingListByBookId((int) book.getBookId())
        .get(0);
    assertEquals(userBookMapping1, userBookMapping2);
    UserBookMapping userBookMapping3 = userBookMappingMapper
        .selectUserBookMappingListByUserId((int) user2.getUserId())
        .get(0);
    assertEquals(userBookMapping1, userBookMapping3);

    userBookMappingMapper.deleteUserBookMapping(userBookMapping1);
    bookMapper.deleteBookById((int) book.getBookId());
    bookCategoryMapper.deleteBookCategoryById((int) bookCategory.getCategoryId());
    userMapper.deleteUserById((int) user1.getUserId());
    userMapper.deleteUserById((int) user2.getUserId());
  }

  @Test
  public void updateUserBookMappingTest() {
    User user1 = new User();
    user1.setUserEmail("zhangsan1@example.com");
    user1.setUserPassword("123456");
    user1.setUserName("zhangsan1");
    user1.setUserAvatarUrl("https://picsum.photos/50/50");
    userMapper.insertOneUser(user1);
    User user2 = new User();
    user2.setUserEmail("zhangsan2@example.com");
    user2.setUserPassword("123456");
    user2.setUserName("zhangsan2");
    user2.setUserAvatarUrl("https://picsum.photos/50/50");
    userMapper.insertOneUser(user2);

    BookCategory bookCategory = new BookCategory();
    bookCategory.setCategoryName("分类1");
    bookCategoryMapper.insertOneBookCategory(bookCategory);
    Book book1 = new Book();
    book1.setBookName("图书名");
    book1.setBookAuthor(user1.getUserId());
    book1.setBookCategory(bookCategory.getCategoryId());
    book1.setBookBaidescription("图书描述图书描述图书描述图书描述");
    book1.setBookCoverUrl("https://picsum.photos/400/400");
    bookMapper.insertOneBook(book1);
    Book book2 = new Book();
    book2.setBookName("图书名");
    book2.setBookAuthor(user1.getUserId());
    book2.setBookCategory(bookCategory.getCategoryId());
    book2.setBookBaidescription("图书描述图书描述图书描述图书描述");
    book2.setBookCoverUrl("https://picsum.photos/400/400");
    bookMapper.insertOneBook(book2);

    UserBookMapping userBookMapping1 = new UserBookMapping();
    userBookMapping1.setBookId(book1.getBookId());
    userBookMapping1.setUserId(user2.getUserId());
    userBookMappingMapper.insertOneUserBookMapping(userBookMapping1);
    UserBookMapping userBookMapping2 = new UserBookMapping();
    userBookMapping2.setBookId(book1.getBookId());
    userBookMapping2.setUserId(user2.getUserId());

    userBookMappingMapper.updateUserBookMapping(userBookMapping1,userBookMapping2);

    UserBookMapping userBookMapping3 = userBookMappingMapper
        .selectUserBookMappingListByBookId((int) book1.getBookId())
        .get(0);
    assertEquals(userBookMapping3, userBookMapping3);

    userBookMappingMapper.deleteUserBookMapping(userBookMapping1);
    bookMapper.deleteBookById((int) book1.getBookId());
    bookMapper.deleteBookById((int) book2.getBookId());
    bookCategoryMapper.deleteBookCategoryById((int) bookCategory.getCategoryId());
    userMapper.deleteUserById((int) user1.getUserId());
    userMapper.deleteUserById((int) user2.getUserId());
  }
}
