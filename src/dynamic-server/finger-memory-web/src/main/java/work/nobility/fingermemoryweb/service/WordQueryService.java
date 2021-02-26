package work.nobility.fingermemoryweb.service;

import work.nobility.fingermemoryweb.entity.LexiconWordMapping;
import work.nobility.fingermemoryweb.exception.GlobalException;
import work.nobility.fingermemoryweb.model.request.SearchWordInfo;
import work.nobility.fingermemoryweb.model.request.WordInfo;
import work.nobility.fingermemoryweb.model.response.SimpleWordCard;
import work.nobility.fingermemoryweb.model.response.UserInfo;
import work.nobility.fingermemoryweb.model.response.WordCard;

import java.util.List;

public interface WordQueryService {
  List<WordCard> wordCard(SearchWordInfo searchWordInfo);

  List<SimpleWordCard> associates(String word);

  List<String> bookCategory();

  void addWord(WordInfo wordInfo, UserInfo userInfo) throws GlobalException;

  void updateWord(WordInfo wordInfo, UserInfo userInfo) throws GlobalException;

  void deleteWord(Integer id, UserInfo userInfo);

  void collectingWord(LexiconWordMapping lexiconWordMapping, UserInfo userInfo) throws GlobalException;

  void cancelCollectingLexicon(LexiconWordMapping lexiconWordMapping, UserInfo userInfo) throws GlobalException;
}
