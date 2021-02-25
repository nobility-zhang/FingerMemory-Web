package work.nobility.fingermemoryweb.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import work.nobility.fingermemoryweb.entity.Note;
import work.nobility.fingermemoryweb.entity.Word;
import work.nobility.fingermemoryweb.entity.WordCategory;
import work.nobility.fingermemoryweb.mapper.NoteMapper;
import work.nobility.fingermemoryweb.mapper.WordCategoryMapper;
import work.nobility.fingermemoryweb.mapper.WordMapper;
import work.nobility.fingermemoryweb.model.request.SearchWordInfo;
import work.nobility.fingermemoryweb.model.response.SimpleWordCard;
import work.nobility.fingermemoryweb.model.response.WordCard;
import work.nobility.fingermemoryweb.service.WordQueryService;

import java.util.ArrayList;
import java.util.List;

@Service
public class WordQueryServiceImpl implements WordQueryService {
  @Autowired
  private WordMapper wordMapper;
  @Autowired
  private WordCategoryMapper wordCategoryMapper;
  @Autowired
  private NoteMapper noteMapper;

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
      WordCard wordCard = this.convertWordToWordCard(word);
      wordCards.add(wordCard);
    }

    return wordCards;
  }

  private WordCard convertWordToWordCard(Word word) {
    Note note = noteMapper.selectNoteByWordIdRandomNoteAuthorId(word.getWordId());
    WordCard wordCard = new WordCard();
    wordCard.setId((int) word.getWordId().longValue());
    wordCard.setEnglish(word.getWordEnglish());
    wordCard.setTranslation(word.getWordTranslate());
    wordCard.setDescription(word.getWordBaidescription());
    wordCard.setImageUrl(word.getWordCoverUrl());
    if (note == null) {
      wordCard.setNote("该单词还没有用户为其添加笔记");
    } else {
      wordCard.setNote(note.getNoteBaidescription());
    }
    return wordCard;
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
      SimpleWordCard simpleWordCard = this.convertWordCardToSimpleWordCard(wordCard);
      simpleWordCards.add(simpleWordCard);
    }
    return simpleWordCards;
  }

  private SimpleWordCard convertWordCardToSimpleWordCard(WordCard wordCard) {
    SimpleWordCard simpleWordCard = new SimpleWordCard();
    simpleWordCard.setId((int) wordCard.getId().longValue());
    simpleWordCard.setEnglish(wordCard.getEnglish());
    simpleWordCard.setTranslation(wordCard.getTranslation());
    simpleWordCard.setDescription(wordCard.getDescription());
    return simpleWordCard;
  }
}
