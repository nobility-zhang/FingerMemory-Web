package work.nobility.fingermemoryweb.service;

import work.nobility.fingermemoryweb.model.request.SearchBookInfo;
import work.nobility.fingermemoryweb.model.response.BookItem;

import java.util.List;

public interface BookListService {
  List<String> bookCategory();

  List<BookItem> bookList(SearchBookInfo searchBookInfo);
}
