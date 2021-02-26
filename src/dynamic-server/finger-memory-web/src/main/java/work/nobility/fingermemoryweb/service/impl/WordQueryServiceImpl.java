package work.nobility.fingermemoryweb.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import work.nobility.fingermemoryweb.entity.Lexicon;
import work.nobility.fingermemoryweb.entity.LexiconWordMapping;
import work.nobility.fingermemoryweb.entity.Word;
import work.nobility.fingermemoryweb.entity.WordCategory;
import work.nobility.fingermemoryweb.exception.ExceptionEnum;
import work.nobility.fingermemoryweb.exception.GlobalException;
import work.nobility.fingermemoryweb.mapper.LexiconMapper;
import work.nobility.fingermemoryweb.mapper.LexiconWordMappingMapper;
import work.nobility.fingermemoryweb.mapper.WordCategoryMapper;
import work.nobility.fingermemoryweb.mapper.WordMapper;
import work.nobility.fingermemoryweb.model.request.SearchWordInfo;
import work.nobility.fingermemoryweb.model.request.WordInfo;
import work.nobility.fingermemoryweb.model.response.SimpleWordCard;
import work.nobility.fingermemoryweb.model.response.UserInfo;
import work.nobility.fingermemoryweb.model.response.WordCard;
import work.nobility.fingermemoryweb.service.WordQueryService;
import work.nobility.fingermemoryweb.utils.EntityConvertModelUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class WordQueryServiceImpl implements WordQueryService {
  @Autowired
  private WordMapper wordMapper;
  @Autowired
  private WordCategoryMapper wordCategoryMapper;
  @Autowired
  private LexiconWordMappingMapper lexiconWordMappingMapper;
  @Autowired
  LexiconMapper lexiconMapper;
  @Autowired
  private EntityConvertModelUtils entityConvertModelUtils;

  @Override
  public List<WordCard> wordCard(SearchWordInfo searchWordInfo) {
    String category = searchWordInfo.getCategory();
    WordCategory wordCategory;
    if (category == null) {
      wordCategory = new WordCategory();
      wordCategory.setCategoryId(null);
      wordCategory.setCategoryName(null);
    } else {
      wordCategory = wordCategoryMapper.selectWordCategoryByName(category);
    }

    List<Word> words;

    words = wordMapper.selectWord(
        searchWordInfo.getWord(),
        wordCategory.getCategoryId(),
        searchWordInfo.getPrefix(),
        searchWordInfo.getSuffix(),
        searchWordInfo.getContain()
    );

    if (words.size() == 0) {
      return null;
    }

    List<WordCard> wordCards = new ArrayList<>();
    for (Word word : words) {
      WordCard wordCard = entityConvertModelUtils.convertWordToWordCard(word);
      wordCards.add(wordCard);
    }

    return wordCards;
  }

  @Override
  public List<SimpleWordCard> associates(String word) {
    SearchWordInfo searchWordInfo = new SearchWordInfo();
    searchWordInfo.setContain(word);
    List<WordCard> wordCards = this.wordCard(searchWordInfo);
    if (wordCards.size() == 0) {
      return null;
    }

    List<SimpleWordCard> simpleWordCards = new ArrayList<>();
    for (WordCard wordCard : wordCards) {
      SimpleWordCard simpleWordCard = entityConvertModelUtils.convertWordCardToSimpleWordCard(wordCard);
      simpleWordCards.add(simpleWordCard);
    }
    return simpleWordCards;
  }

  @Override
  public List<String> bookCategory() {
    List<WordCategory> wordCategories = wordCategoryMapper.selectAll();
    List<String> categories = new ArrayList<>();
    for (WordCategory wordCategory : wordCategories) {
      categories.add(wordCategory.getCategoryName());
    }
    return categories;
  }

  @Override
  public void addWord(WordInfo wordInfo, UserInfo userInfo) throws GlobalException {
    // TODO userInfo备用，給上传单词用户积分奖励之类的功能
    Word word = entityConvertModelUtils.convertWordInfoToWord(wordInfo);
    wordMapper.insertOneWord(word);
  }

  @Override
  public void updateWord(WordInfo wordInfo, UserInfo userInfo) throws GlobalException {
    // TODO userInfo备用，給修正单词用户积分奖励之类的功能
    Word word = entityConvertModelUtils.convertWordInfoToWord(wordInfo);
    word.setWordId(wordInfo.getId().longValue());
    wordMapper.updateWord(word);
  }

  @Override
  public void deleteWord(Integer id, UserInfo userInfo) {
    // TODO userInfo备用，审核用户
    wordMapper.deleteWordById(id.longValue());
  }

  @Override
  public void collectingWord(LexiconWordMapping lexiconWordMapping, UserInfo userInfo) throws GlobalException {
    Lexicon lexicon = lexiconMapper.selectLexiconById(lexiconWordMapping.getLexiconId());
    if (lexicon.getLexiconAuthor().equals(userInfo.getId().longValue())) {
      if (lexiconWordMappingMapper.selectLexiconWordMappingList(lexiconWordMapping) == null) {
        lexiconWordMappingMapper.insertOneLexiconWordMapping(lexiconWordMapping);
      } else {
        throw new GlobalException(ExceptionEnum.Repetitive_Operation_Error);
      }
    } else {
      throw new GlobalException(ExceptionEnum.Update_Error);
    }
  }

  @Override
  public void cancelCollectingLexicon(LexiconWordMapping lexiconWordMapping, UserInfo userInfo) throws GlobalException {
    Lexicon lexicon = lexiconMapper.selectLexiconById(lexiconWordMapping.getLexiconId());
    if (lexicon.getLexiconAuthor().equals(userInfo.getId().longValue())) {
      lexiconWordMappingMapper.deleteLexiconWordMapping(lexiconWordMapping);
    } else {
      throw new GlobalException(ExceptionEnum.Update_Error);
    }
  }
}
