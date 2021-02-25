package work.nobility.fingermemoryweb.controller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import work.nobility.fingermemoryweb.common.ApiRestResponse;
import work.nobility.fingermemoryweb.model.request.SearchBookInfo;
import work.nobility.fingermemoryweb.model.response.BookItem;
import work.nobility.fingermemoryweb.service.BookListService;

import java.util.List;

@RestController
public class BookListController {
  @Autowired
  BookListService bookListService;

  @GetMapping("/book-list")
  public ApiRestResponse<List<BookItem>> bookList(SearchBookInfo searchBookInfo) {
    List<BookItem> bookItems = bookListService.bookList(searchBookInfo);
    return ApiRestResponse.success(bookItems);
  }

  @GetMapping("/book-category")
  public ApiRestResponse<List<String>> bookCategory() {
    List<String> category = bookListService.bookCategory();
    return ApiRestResponse.success(category);
  }
}
