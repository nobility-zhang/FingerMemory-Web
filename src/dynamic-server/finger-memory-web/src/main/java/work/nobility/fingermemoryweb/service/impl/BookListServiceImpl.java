package work.nobility.fingermemoryweb.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import work.nobility.fingermemoryweb.entity.Book;
import work.nobility.fingermemoryweb.entity.BookCategory;
import work.nobility.fingermemoryweb.entity.UserBookMapping;
import work.nobility.fingermemoryweb.exception.ExceptionEnum;
import work.nobility.fingermemoryweb.exception.GlobalException;
import work.nobility.fingermemoryweb.mapper.BookCategoryMapper;
import work.nobility.fingermemoryweb.mapper.BookMapper;
import work.nobility.fingermemoryweb.mapper.UserBookMappingMapper;
import work.nobility.fingermemoryweb.model.request.BookInfo;
import work.nobility.fingermemoryweb.model.request.SearchBookInfo;
import work.nobility.fingermemoryweb.model.response.BookItem;
import work.nobility.fingermemoryweb.service.BookListService;
import work.nobility.fingermemoryweb.utils.EntityConvertModelUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class BookListServiceImpl implements BookListService {
  @Autowired
  private BookMapper bookMapper;
  @Autowired
  private BookCategoryMapper bookCategoryMapper;
  @Autowired
  private EntityConvertModelUtils entityConvertModelUtils;
  @Autowired
  private UserBookMappingMapper userBookMappingMapper;

  @Override
  public List<String> bookCategory() {
    List<BookCategory> bookCategories = bookCategoryMapper.selectAll();
    List<String> categories = new ArrayList<>();
    for (BookCategory bookCategory : bookCategories) {
      categories.add(bookCategory.getCategoryName());
    }
    return categories;
  }

  @Override
  public List<BookItem> bookList(SearchBookInfo searchBookInfo) throws GlobalException {
    String category = searchBookInfo.getCategory();
    BookCategory bookCategory;
    if (category == null) {
      bookCategory = new BookCategory();
      bookCategory.setCategoryName(null);
      bookCategory.setCategoryId(null);
    } else {
      bookCategory = bookCategoryMapper.selectBookCategoryByName(category);
    }
    List<Book> books = bookMapper.selectBook(
        searchBookInfo.getName(),
        bookCategory.getCategoryId(),
        searchBookInfo.getPrefix(),
        searchBookInfo.getPrefix(),
        searchBookInfo.getContain()
    );
    List<BookItem> bookItems = new ArrayList<>();
    for (Book book : books) {
      BookItem bookItem = entityConvertModelUtils.convertBookToBookItem(book);
      bookItems.add(bookItem);
    }
    return bookItems;
  }

  @Override
  public void addBook(BookInfo bookInfo, Integer authorId) throws GlobalException {
    Book book = entityConvertModelUtils.convertBookInfoToBook(bookInfo, authorId.longValue());
    bookMapper.insertOneBook(book);
  }

  @Override
  public void updateBook(BookInfo bookInfo, Integer authorId) throws GlobalException {
    Book book = bookMapper.selectBookById(bookInfo.getId().longValue());
    if (book.getBookAuthor().equals(authorId.longValue())) {
      book = entityConvertModelUtils.convertBookInfoToBook(bookInfo, authorId.longValue());
      book.setBookId(bookInfo.getId().longValue());
      bookMapper.updateBook(book);
    } else {
      throw new GlobalException(ExceptionEnum.Update_Error);
    }
  }

  @Override
  public void deleteBook(Integer id, Integer authorId) throws GlobalException {
    Book book = bookMapper.selectBookById(id.longValue());
    if (book.getBookAuthor().equals(authorId.longValue())) {
      bookMapper.deleteBookById(id.longValue());
    } else {
      throw new GlobalException(ExceptionEnum.Delete_Error);
    }
  }

  @Override
  public void collectingBook(Integer id, Integer authorId) throws GlobalException {
    UserBookMapping userBookMapping = new UserBookMapping(
        authorId.longValue(),
        id.longValue()
    );
    if (userBookMappingMapper.selectUserBookMApping(userBookMapping) == null) {
      userBookMappingMapper.insertOneUserBookMapping(userBookMapping);
    } else {
      throw new GlobalException(ExceptionEnum.Repetitive_Operation_Error);
    }
  }

  @Override
  public void cancelCollectingBook(Integer id, Integer authorId) {
    UserBookMapping userBookMapping = new UserBookMapping(
        authorId.longValue(),
        id.longValue()
    );
    userBookMappingMapper.deleteUserBookMapping(userBookMapping);
  }
}
