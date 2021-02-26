package work.nobility.fingermemoryweb.controller;

import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import work.nobility.fingermemoryweb.common.ApiRestResponse;
import work.nobility.fingermemoryweb.common.Constant;
import work.nobility.fingermemoryweb.entity.LexiconWordMapping;
import work.nobility.fingermemoryweb.entity.Word;
import work.nobility.fingermemoryweb.exception.GlobalException;
import work.nobility.fingermemoryweb.model.request.SearchWordInfo;
import work.nobility.fingermemoryweb.model.request.WordInfo;
import work.nobility.fingermemoryweb.model.response.SimpleWordCard;
import work.nobility.fingermemoryweb.model.response.UserInfo;
import work.nobility.fingermemoryweb.model.response.WordCard;
import work.nobility.fingermemoryweb.service.WordQueryService;
import work.nobility.fingermemoryweb.utils.RedisHttpSession;

import javax.servlet.http.HttpSession;
import java.util.List;

@RestController
public class WordQueryController {
  @Autowired
  private WordQueryService wordQueryService;
  @Autowired
  private RedisHttpSession redisHttpSession;

  @GetMapping("/word-card")
  public ApiRestResponse<List<WordCard>> wordCard(SearchWordInfo searchWordInfo) {
    List<WordCard> wordCards = wordQueryService.wordCard(searchWordInfo);
    return ApiRestResponse.success(wordCards);
  }

  @GetMapping("/associates")
  public ApiRestResponse<List<SimpleWordCard>> associates(@RequestParam String word) {
    List<SimpleWordCard> associates = wordQueryService.associates(word);
    return ApiRestResponse.success(associates);
  }

  @GetMapping("/word-category")
  public ApiRestResponse<List<String>> bookCategory() {
    List<String> category = wordQueryService.bookCategory();
    return ApiRestResponse.success(category);
  }

  @PostMapping("/word")
  public ApiRestResponse<Object> addWord(@ApiParam("无需传id") @RequestBody WordInfo wordInfo,
                                         HttpSession session) throws GlobalException {
    redisHttpSession.setSession(session);
    UserInfo userInfo = redisHttpSession.getAttribute(Constant.UID, UserInfo.class);
    wordQueryService.addWord(wordInfo, userInfo);
    return ApiRestResponse.success();
  }

  @PutMapping("/word")
  public ApiRestResponse<Object> updateWord(@RequestBody WordInfo wordInfo,
                                            HttpSession session) throws GlobalException {
    redisHttpSession.setSession(session);
    UserInfo userInfo = redisHttpSession.getAttribute(Constant.UID, UserInfo.class);
    wordQueryService.updateWord(wordInfo, userInfo);
    return ApiRestResponse.success();
  }

  @DeleteMapping("/word")
  public ApiRestResponse<Object> deleteWord(@ApiParam("只接收id") @RequestBody WordInfo wordInfo,
                                            HttpSession session) {
    redisHttpSession.setSession(session);
    UserInfo userInfo = redisHttpSession.getAttribute(Constant.UID, UserInfo.class);
    wordQueryService.deleteWord(wordInfo.getId(), userInfo);
    return ApiRestResponse.success();
  }

  @PostMapping("/collecting-word")
  public ApiRestResponse<Object> collectingWord(@RequestBody LexiconWordMapping lexiconWordMapping,
                                                HttpSession session) throws GlobalException {
    redisHttpSession.setSession(session);
    UserInfo userInfo = redisHttpSession.getAttribute(Constant.UID, UserInfo.class);
    wordQueryService.collectingWord(lexiconWordMapping, userInfo);
    return ApiRestResponse.success();
  }

  @DeleteMapping("/collecting-word")
  public ApiRestResponse<Object> cancelCollectingLexicon(@RequestBody LexiconWordMapping lexiconWordMapping,
                                                         HttpSession session) throws GlobalException {
    redisHttpSession.setSession(session);
    UserInfo userInfo = redisHttpSession.getAttribute(Constant.UID, UserInfo.class);
    wordQueryService.cancelCollectingLexicon(lexiconWordMapping, userInfo);
    return ApiRestResponse.success();
  }
}
