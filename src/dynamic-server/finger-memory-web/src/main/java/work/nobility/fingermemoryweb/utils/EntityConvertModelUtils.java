package work.nobility.fingermemoryweb.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import work.nobility.fingermemoryweb.entity.Book;
import work.nobility.fingermemoryweb.entity.Lexicon;
import work.nobility.fingermemoryweb.entity.User;
import work.nobility.fingermemoryweb.entity.Word;
import work.nobility.fingermemoryweb.mapper.UserMapper;
import work.nobility.fingermemoryweb.model.response.BookItem;
import work.nobility.fingermemoryweb.model.response.LexiconItem;
import work.nobility.fingermemoryweb.model.response.UserInfo;
import work.nobility.fingermemoryweb.model.response.WordItem;

@Component
public class EntityConvertModelUtils {
  @Autowired
  public UserMapper userMapper;

  public BookItem convertBookToBookItem(Book book) {
    User user = userMapper.selectUserById(book.getBookAuthor());
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
}
