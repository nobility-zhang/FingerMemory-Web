package work.nobility.fingermemoryweb.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import work.nobility.fingermemoryweb.common.ApiRestResponse;
import work.nobility.fingermemoryweb.model.request.SearchWordInfo;
import work.nobility.fingermemoryweb.model.response.SimpleWordCard;
import work.nobility.fingermemoryweb.model.response.WordCard;
import work.nobility.fingermemoryweb.service.WordQueryService;

import java.util.List;

@RestController
public class WordQueryController {
  @Autowired
  private WordQueryService wordQueryService;

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
}
