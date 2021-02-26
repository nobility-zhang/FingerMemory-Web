package work.nobility.fingermemoryweb.controller;


import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import work.nobility.fingermemoryweb.common.ApiRestResponse;
import work.nobility.fingermemoryweb.common.Constant;
import work.nobility.fingermemoryweb.configuration.SessionUtilsConfig;
import work.nobility.fingermemoryweb.exception.GlobalException;
import work.nobility.fingermemoryweb.model.request.BookInfo;
import work.nobility.fingermemoryweb.model.request.SearchBookInfo;
import work.nobility.fingermemoryweb.model.response.BookItem;
import work.nobility.fingermemoryweb.model.response.UserInfo;
import work.nobility.fingermemoryweb.service.BookListService;
import work.nobility.fingermemoryweb.utils.RedisHttpSession;

import javax.servlet.http.HttpSession;
import java.util.List;

@RestController
public class BookListController {
  @Autowired
  private BookListService bookListService;
  @Autowired
  private SessionUtilsConfig sessionUtilsConfig;

  @GetMapping("/book-list")
  public ApiRestResponse<List<BookItem>> bookList(SearchBookInfo searchBookInfo) throws GlobalException {
    List<BookItem> bookItems = bookListService.bookList(searchBookInfo);
    return ApiRestResponse.success(bookItems);
  }

  @GetMapping("/book-category")
  public ApiRestResponse<List<String>> bookCategory() {
    List<String> category = bookListService.bookCategory();
    return ApiRestResponse.success(category);
  }

  @PostMapping("/book")
  public ApiRestResponse<Object> addBook(@ApiParam("无需传id") @RequestBody BookInfo bookInfo,
                                         HttpSession session) throws GlobalException {
    RedisHttpSession redisHttpSession = sessionUtilsConfig.getRedisHttpSessionBean();
    redisHttpSession.setSession(session);
    UserInfo userInfo = redisHttpSession.getAttribute(Constant.UID, UserInfo.class);
    Integer authorId = userInfo.getId();
    bookListService.addBook(bookInfo, authorId);
    return ApiRestResponse.success();
  }

  @PutMapping("/book")
  public ApiRestResponse<Object> updateBook(@RequestBody BookInfo bookInfo,
                                            HttpSession session) throws GlobalException {
    RedisHttpSession redisHttpSession = sessionUtilsConfig.getRedisHttpSessionBean();
    redisHttpSession.setSession(session);
    UserInfo userInfo = redisHttpSession.getAttribute(Constant.UID, UserInfo.class);
    Integer authorId = userInfo.getId();
    bookListService.updateBook(bookInfo, authorId);
    return ApiRestResponse.success();
  }

  @DeleteMapping("/book")
  public ApiRestResponse<Object> deleteBook(@ApiParam("只接收id") @RequestBody BookInfo bookInfo,
                                            HttpSession session) throws GlobalException {
    RedisHttpSession redisHttpSession = sessionUtilsConfig.getRedisHttpSessionBean();
    redisHttpSession.setSession(session);
    UserInfo userInfo = redisHttpSession.getAttribute(Constant.UID, UserInfo.class);
    Integer authorId = userInfo.getId();
    bookListService.deleteBook(bookInfo.getId(), authorId);
    return ApiRestResponse.success();
  }

  @PostMapping("/collecting-book")
  public ApiRestResponse<Object> collectingBook(@ApiParam("只接收id") @RequestBody BookInfo bookInfo,
                                                HttpSession session) throws GlobalException {
    RedisHttpSession redisHttpSession = sessionUtilsConfig.getRedisHttpSessionBean();
    redisHttpSession.setSession(session);
    UserInfo userInfo = redisHttpSession.getAttribute(Constant.UID, UserInfo.class);
    Integer authorId = userInfo.getId();
    bookListService.collectingBook(bookInfo.getId(), authorId);
    return ApiRestResponse.success();
  }

  @DeleteMapping("/collecting-book")
  public ApiRestResponse<Object> cancelCollectingBook(@ApiParam("只接收id") @RequestBody BookInfo bookInfo,
                                                      HttpSession session) {
    RedisHttpSession redisHttpSession = sessionUtilsConfig.getRedisHttpSessionBean();
    redisHttpSession.setSession(session);
    UserInfo userInfo = redisHttpSession.getAttribute(Constant.UID, UserInfo.class);
    Integer authorId = userInfo.getId();
    bookListService.cancelCollectingBook(bookInfo.getId(), authorId);
    return ApiRestResponse.success();
  }
}
