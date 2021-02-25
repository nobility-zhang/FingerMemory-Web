package work.nobility.fingermemoryweb.service;

import work.nobility.fingermemoryweb.model.request.SearchWordInfo;
import work.nobility.fingermemoryweb.model.response.SimpleWordCard;
import work.nobility.fingermemoryweb.model.response.WordCard;

import java.util.List;

public interface WordQueryService {
  List<WordCard> wordCard(SearchWordInfo searchWordInfo);

  List<SimpleWordCard> associates(String word);
}
