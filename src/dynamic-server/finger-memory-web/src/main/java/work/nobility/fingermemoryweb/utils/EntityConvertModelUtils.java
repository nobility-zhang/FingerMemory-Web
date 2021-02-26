package work.nobility.fingermemoryweb.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import work.nobility.fingermemoryweb.entity.*;
import work.nobility.fingermemoryweb.exception.ExceptionEnum;
import work.nobility.fingermemoryweb.exception.GlobalException;
import work.nobility.fingermemoryweb.mapper.BookCategoryMapper;
import work.nobility.fingermemoryweb.mapper.NoteMapper;
import work.nobility.fingermemoryweb.mapper.UserMapper;
import work.nobility.fingermemoryweb.mapper.WordCategoryMapper;
import work.nobility.fingermemoryweb.model.request.BookInfo;
import work.nobility.fingermemoryweb.model.request.LexiconInfo;
import work.nobility.fingermemoryweb.model.request.WordInfo;
import work.nobility.fingermemoryweb.model.response.*;

@Component
public class EntityConvertModelUtils {
  @Autowired
  public UserMapper userMapper;
  @Autowired
  private NoteMapper noteMapper;
  @Autowired
  private BookCategoryMapper bookCategoryMapper;
  @Autowired
  private WordCategoryMapper wordCategoryMapper;

  public BookItem convertBookToBookItem(Book book) throws GlobalException {
    User user = userMapper.selectUserById(book.getBookAuthor());
    if (user == null) {
      throw new GlobalException(ExceptionEnum.User_Not_Exists);
    }
    BookItem bookItem = new BookItem();
    bookItem.setId((int) book.getBookId().longValue());
    bookItem.setName(book.getBookName());
    bookItem.setDescription(book.getBookBaidescription());
    bookItem.setAuthor(user.getUserName());
    bookItem.setCoverUrl(book.getBookCoverUrl());
    return bookItem;
  }

  public LexiconItem convertLexiconToLexiconItem(Lexicon lexicon, UserInfo userInfo) {
    LexiconItem lexiconItem = new LexiconItem();
    lexiconItem.setId((int) lexicon.getLexiconId().longValue());
    lexiconItem.setName(lexicon.getLexiconName());
    lexiconItem.setAuthor(userInfo.getUserName());
    lexiconItem.setDescription(lexicon.getLexiconBaidescription());
    lexiconItem.setCoverUrl(lexicon.getLexiconCoverUrl());
    return lexiconItem;
  }

  public LexiconItem convertLexiconToLexiconItem(Lexicon lexicon) {
    User user = userMapper.selectUserById(lexicon.getLexiconAuthor());
    LexiconItem lexiconItem = new LexiconItem();
    lexiconItem.setId((int) lexicon.getLexiconId().longValue());
    lexiconItem.setName(lexicon.getLexiconName());
    lexiconItem.setAuthor(user.getUserName());
    lexiconItem.setDescription(lexicon.getLexiconBaidescription());
    lexiconItem.setCoverUrl(lexicon.getLexiconCoverUrl());
    return lexiconItem;
  }

  public WordItem convertWordToWordItem(Word word) {
    WordItem wordItem = new WordItem();
    wordItem.setId((int) word.getWordId().longValue());
    wordItem.setEnglish(word.getWordEnglish());
    wordItem.setTranslation(word.getWordTranslate());
    return wordItem;
  }

  public WordCard convertWordToWordCard(Word word) {
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

  public SimpleWordCard convertWordCardToSimpleWordCard(WordCard wordCard) {
    SimpleWordCard simpleWordCard = new SimpleWordCard();
    simpleWordCard.setId((int) wordCard.getId().longValue());
    simpleWordCard.setEnglish(wordCard.getEnglish());
    simpleWordCard.setTranslation(wordCard.getTranslation());
    simpleWordCard.setDescription(wordCard.getDescription());
    return simpleWordCard;
  }

  public Book convertBookInfoToBook(BookInfo bookInfo, Long authorId) throws GlobalException {
    BookCategory bookCategory = bookCategoryMapper.selectBookCategoryByName(bookInfo.getCategory());
    if (bookCategory == null) {
      throw new GlobalException(ExceptionEnum.Category_Not_Exists);
    }
    Book book = new Book();
    book.setBookName(bookInfo.getName());
    book.setBookBaidescription(bookInfo.getDescription());
    book.setBookCoverUrl(bookInfo.getCoverUrl());
    book.setBookCategory(bookCategory.getCategoryId());
    book.setBookAuthor(authorId);
    return book;
  }

  public Lexicon convertLexiconInfoToLexicon(LexiconInfo lexiconInfo, Long authorId) {
    Lexicon lexicon = new Lexicon();
    lexicon.setLexiconName(lexiconInfo.getName());
    lexicon.setLexiconBaidescription(lexiconInfo.getDescription());
    lexicon.setLexiconCoverUrl(lexiconInfo.getDescription());
    lexicon.setLexiconAuthor(authorId);
    return lexicon;
  }

  public Word convertWordInfoToWord(WordInfo wordInfo) throws GlobalException {
    String category = wordInfo.getCategory();
    WordCategory wordCategory = wordCategoryMapper.selectWordCategoryByName(category);
    if (wordCategory == null) {
      throw new GlobalException(ExceptionEnum.Category_Not_Exists);
    }
    Word word = new Word();
    word.setWordEnglish(wordInfo.getEnglish());
    word.setWordTranslate(wordInfo.getTranslate());
    word.setWordBaidescription(wordInfo.getDescription());
    word.setWordCoverUrl(wordInfo.getCoverUrl());
    word.setWordCategory(wordCategory.getCategoryId());
    return word;
  }
}
