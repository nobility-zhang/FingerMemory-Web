package work.nobility.fingermemoryweb.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import work.nobility.fingermemoryweb.entity.Book;
import work.nobility.fingermemoryweb.entity.BookCategory;
import work.nobility.fingermemoryweb.entity.User;
import work.nobility.fingermemoryweb.mapper.BookCategoryMapper;
import work.nobility.fingermemoryweb.mapper.BookMapper;
import work.nobility.fingermemoryweb.mapper.UserMapper;
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

  @Override
  public List<String> bookCategory() {
    List<BookCategory> bookCategories = bookCategoryMapper.selectAll();
    List<String> Categories = new ArrayList<>();
    for (BookCategory bookCategory : bookCategories) {
      Categories.add(bookCategory.getCategoryName());
    }
    return Categories;
  }

  @Override
  public List<BookItem> bookList(SearchBookInfo searchBookInfo) {
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
}
