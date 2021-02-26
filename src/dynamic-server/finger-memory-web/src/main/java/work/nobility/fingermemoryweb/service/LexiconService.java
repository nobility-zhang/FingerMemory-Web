package work.nobility.fingermemoryweb.service;

import work.nobility.fingermemoryweb.exception.GlobalException;
import work.nobility.fingermemoryweb.model.request.LexiconInfo;
import work.nobility.fingermemoryweb.model.request.SearchLexiconInfo;
import work.nobility.fingermemoryweb.model.response.LexiconItem;

import java.util.List;

public interface LexiconService {
  List<LexiconItem> lexiconList(SearchLexiconInfo searchLexiconInfo);

  void addLexicon(LexiconInfo lexiconInfo, Integer authorId);

  void updateLexicon(LexiconInfo lexiconInfo, Integer authorId) throws GlobalException;

  void deleteLexicon(Integer id, Integer authorId) throws GlobalException;

  void collectingLexicon(Integer id, Integer authorId) throws GlobalException;

  void cancelCollectingLexicon(Integer id, Integer authorId);
}
