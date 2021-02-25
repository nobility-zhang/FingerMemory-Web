package work.nobility.fingermemoryweb.service;

import work.nobility.fingermemoryweb.model.response.BookItem;
import work.nobility.fingermemoryweb.model.response.LexiconItem;
import work.nobility.fingermemoryweb.model.response.UserInfo;

import java.util.List;

public interface MeService {
  List<LexiconItem> meLexicon(UserInfo userInfo);

  List<LexiconItem> meCollectingLexicon(UserInfo userInfo);

  List<BookItem> meCollectingBook(UserInfo userInfo);
}
