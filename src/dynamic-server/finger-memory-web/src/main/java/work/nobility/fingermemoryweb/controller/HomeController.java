package work.nobility.fingermemoryweb.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import work.nobility.fingermemoryweb.common.ApiRestResponse;
import work.nobility.fingermemoryweb.model.response.WordItem;
import work.nobility.fingermemoryweb.service.HomeService;

import java.util.List;

@RestController
public class HomeController {
  @Autowired
  private HomeService homeService;

  @GetMapping("/core-memory")
  public ApiRestResponse<List<WordItem>> coreMemory() {
    List<WordItem> wordItems = homeService.coreMemory();
    return ApiRestResponse.success(wordItems);
  }

  @GetMapping("/lexicon-content")
  public ApiRestResponse<List<WordItem>> lexiconContent(Integer lexiconId) {
    List<WordItem> wordItems = homeService.lexiconContent(lexiconId);
    return ApiRestResponse.success(wordItems);
  }
}
