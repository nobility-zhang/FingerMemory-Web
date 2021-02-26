package work.nobility.fingermemoryweb.service;

import work.nobility.fingermemoryweb.exception.GlobalException;
import work.nobility.fingermemoryweb.model.request.BookInfo;
import work.nobility.fingermemoryweb.model.request.SearchBookInfo;
import work.nobility.fingermemoryweb.model.response.BookItem;

import java.util.List;

public interface BookListService {
  List<String> bookCategory();

  List<BookItem> bookList(SearchBookInfo searchBookInfo) throws GlobalException;

  void addBook(BookInfo bookInfo, Integer authorId) throws GlobalException;

  void updateBook(BookInfo bookInfo, Integer authorId) throws GlobalException;

  void deleteBook(Integer id, Integer authorId) throws GlobalException;

  void collectingBook(Integer id, Integer authorId) throws GlobalException;

  void cancelCollectingBook(Integer id, Integer authorId);
}
