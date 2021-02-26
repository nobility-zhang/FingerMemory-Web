package work.nobility.fingermemoryweb.controller;

import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import work.nobility.fingermemoryweb.common.ApiRestResponse;
import work.nobility.fingermemoryweb.common.Constant;
import work.nobility.fingermemoryweb.exception.GlobalException;
import work.nobility.fingermemoryweb.model.request.LexiconInfo;
import work.nobility.fingermemoryweb.model.request.SearchLexiconInfo;
import work.nobility.fingermemoryweb.model.response.LexiconItem;
import work.nobility.fingermemoryweb.model.response.UserInfo;
import work.nobility.fingermemoryweb.service.LexiconService;
import work.nobility.fingermemoryweb.utils.RedisHttpSession;

import javax.servlet.http.HttpSession;
import java.util.List;

@RestController
public class LexiconController {
  @Autowired
  LexiconService lexiconService;
  @Autowired
  private RedisHttpSession redisHttpSession;

  @GetMapping("/lexicon-list")
  public ApiRestResponse<List<LexiconItem>> lexiconList(SearchLexiconInfo searchLexiconInfo) {
    List<LexiconItem> lexiconItems = lexiconService.lexiconList(searchLexiconInfo);
    return ApiRestResponse.success(lexiconItems);
  }

  @PostMapping("/lexicon")
  public ApiRestResponse<Object> addLexicon(@ApiParam("无需传id") @RequestBody LexiconInfo lexiconInfo,
                                            HttpSession session) {
    redisHttpSession.setSession(session);
    UserInfo userInfo = redisHttpSession.getAttribute(Constant.UID, UserInfo.class);
    Integer authorId = userInfo.getId();
    lexiconService.addLexicon(lexiconInfo, authorId);
    return ApiRestResponse.success();
  }

  @PutMapping("/lexicon")
  public ApiRestResponse<Object> updateLexicon(@RequestBody LexiconInfo lexiconInfo,
                                               HttpSession session) throws GlobalException {
    redisHttpSession.setSession(session);
    UserInfo userInfo = redisHttpSession.getAttribute(Constant.UID, UserInfo.class);
    Integer authorId = userInfo.getId();
    lexiconService.updateLexicon(lexiconInfo, authorId);
    return ApiRestResponse.success();
  }

  @DeleteMapping("/lexicon")
  public ApiRestResponse<Object> deleteLexicon(@ApiParam("只接收id") @RequestBody LexiconInfo lexiconInfo,
                                               HttpSession session) throws GlobalException {
    redisHttpSession.setSession(session);
    UserInfo userInfo = redisHttpSession.getAttribute(Constant.UID, UserInfo.class);
    Integer authorId = userInfo.getId();
    lexiconService.deleteLexicon(lexiconInfo.getId(), authorId);
    return ApiRestResponse.success();
  }

  @PostMapping("/collecting-lexicon")
  public ApiRestResponse<Object> collectingLexicon(@ApiParam("只接收id") @RequestBody LexiconInfo lexiconInfo,
                                                   HttpSession session) throws GlobalException {
    redisHttpSession.setSession(session);
    UserInfo userInfo = redisHttpSession.getAttribute(Constant.UID, UserInfo.class);
    Integer authorId = userInfo.getId();
    lexiconService.collectingLexicon(lexiconInfo.getId(), authorId);
    return ApiRestResponse.success();
  }

  @DeleteMapping("/collecting-lexicon")
  public ApiRestResponse<Object> cancelCollectingLexicon(@ApiParam("只接收id") @RequestBody LexiconInfo lexiconInfo,
                                                         HttpSession session) {
    redisHttpSession.setSession(session);
    UserInfo userInfo = redisHttpSession.getAttribute(Constant.UID, UserInfo.class);
    Integer authorId = userInfo.getId();
    lexiconService.cancelCollectingLexicon(lexiconInfo.getId(), authorId);
    return ApiRestResponse.success();
  }
}
