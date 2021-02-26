package work.nobility.fingermemoryweb.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import work.nobility.fingermemoryweb.common.ApiRestResponse;
import work.nobility.fingermemoryweb.common.Constant;
import work.nobility.fingermemoryweb.configuration.SessionUtilsConfig;
import work.nobility.fingermemoryweb.exception.GlobalException;
import work.nobility.fingermemoryweb.model.response.BookItem;
import work.nobility.fingermemoryweb.model.response.LexiconItem;
import work.nobility.fingermemoryweb.model.response.UserInfo;
import work.nobility.fingermemoryweb.service.MeService;
import work.nobility.fingermemoryweb.utils.RedisHttpSession;

import javax.servlet.http.HttpSession;
import java.util.List;

@RestController
public class MeController {
  @Autowired
  private MeService meService;
  @Autowired
  private SessionUtilsConfig sessionUtilsConfig;

  @GetMapping("/me-lexicon")
  public ApiRestResponse<List<LexiconItem>> meLexicon(HttpSession session) {
    RedisHttpSession redisHttpSession = sessionUtilsConfig.getRedisHttpSessionBean();
    redisHttpSession.setSession(session);
    UserInfo userInfo = redisHttpSession.getAttribute(Constant.UID, UserInfo.class);
    List<LexiconItem> lexiconItems = meService.meLexicon(userInfo);
    return ApiRestResponse.success(lexiconItems);
  }

  @GetMapping("/me-collecting-lexicon")
  public ApiRestResponse<List<LexiconItem>> meCollectingLexicon(HttpSession session) {
    RedisHttpSession redisHttpSession = sessionUtilsConfig.getRedisHttpSessionBean();
    redisHttpSession.setSession(session);
    UserInfo userInfo = redisHttpSession.getAttribute(Constant.UID, UserInfo.class);
    List<LexiconItem> lexiconItems = meService.meCollectingLexicon(userInfo);
    return ApiRestResponse.success(lexiconItems);
  }

  @GetMapping("/me-collecting-book")
  public ApiRestResponse<List<BookItem>> meCollectingBook(HttpSession session) throws GlobalException {
    RedisHttpSession redisHttpSession = sessionUtilsConfig.getRedisHttpSessionBean();
    redisHttpSession.setSession(session);
    UserInfo userInfo = redisHttpSession.getAttribute(Constant.UID, UserInfo.class);
    List<BookItem> bookItems = meService.meCollectingBook(userInfo);
    return ApiRestResponse.success(bookItems);
  }
}
