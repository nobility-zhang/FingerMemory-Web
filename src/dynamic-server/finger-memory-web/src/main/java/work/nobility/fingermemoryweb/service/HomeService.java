package work.nobility.fingermemoryweb.service;

import work.nobility.fingermemoryweb.model.response.WordItem;

import java.util.List;

public interface HomeService {
  List<WordItem> coreMemory();

  List<WordItem> lexiconContent(Integer lexiconId);
}
