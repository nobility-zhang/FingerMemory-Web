package work.nobility.fingermemoryweb.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import work.nobility.fingermemoryweb.entity.Word;
import work.nobility.fingermemoryweb.mapper.LexiconMapper;
import work.nobility.fingermemoryweb.mapper.WordMapper;
import work.nobility.fingermemoryweb.model.response.WordItem;
import work.nobility.fingermemoryweb.service.HomeService;
import work.nobility.fingermemoryweb.utils.EntityConvertModelUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class HomeServiceImpl implements HomeService {
  @Autowired
  private WordMapper wordMapper;
  @Autowired
  private LexiconMapper lexiconMapper;
  @Autowired
  private EntityConvertModelUtils entityConvertModelUtils;

  @Override
  public List<WordItem> coreMemory() {
    List<Word> words = wordMapper.selectRandomWord();
    List<WordItem> wordItems = new ArrayList<>();
    for (Word word : words) {
      WordItem wordItem = entityConvertModelUtils.convertWordToWordItem(word);
      wordItems.add(wordItem);
    }
    return wordItems;
  }

  @Override
  public List<WordItem> lexiconContent(Integer lexiconId) {
    List<Word> words = lexiconMapper.selectLexiconByLexiconWord(lexiconId.longValue());
    List<WordItem> wordItems = new ArrayList<>();
    for (Word word : words) {
      WordItem wordItem = entityConvertModelUtils.convertWordToWordItem(word);
      wordItems.add(wordItem);
    }
    return wordItems;
  }
}
