package work.nobility.fingermemoryweb.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import work.nobility.fingermemoryweb.entity.Book;
import work.nobility.fingermemoryweb.entity.Lexicon;
import work.nobility.fingermemoryweb.exception.GlobalException;
import work.nobility.fingermemoryweb.mapper.BookMapper;
import work.nobility.fingermemoryweb.mapper.LexiconMapper;
import work.nobility.fingermemoryweb.model.response.BookItem;
import work.nobility.fingermemoryweb.model.response.LexiconItem;
import work.nobility.fingermemoryweb.model.response.UserInfo;
import work.nobility.fingermemoryweb.service.MeService;
import work.nobility.fingermemoryweb.utils.EntityConvertModelUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class MeServiceImpl implements MeService {
  @Autowired
  private LexiconMapper lexiconMapper;
  @Autowired
  private BookMapper bookMapper;
  @Autowired
  private EntityConvertModelUtils entityConvertModelUtils;

  @Override
  public List<LexiconItem> meLexicon(UserInfo userInfo) {
    List<Lexicon> lexicons = lexiconMapper.selectLexiconByAuthorId(userInfo.getId().longValue());
    List<LexiconItem> lexiconItems = new ArrayList<>();
    for (Lexicon lexicon : lexicons) {
      LexiconItem lexiconItem = entityConvertModelUtils.convertLexiconToLexiconItem(lexicon, userInfo);
      lexiconItems.add(lexiconItem);
    }
    return lexiconItems;
  }

  @Override
  public List<LexiconItem> meCollectingLexicon(UserInfo userInfo) {
    List<Lexicon> lexicons = lexiconMapper.selectLexiconByUserLexicon(userInfo.getId().longValue());
    List<LexiconItem> lexiconItems = new ArrayList<>();
    for (Lexicon lexicon : lexicons) {
      LexiconItem lexiconItem = entityConvertModelUtils.convertLexiconToLexiconItem(lexicon);
      lexiconItems.add(lexiconItem);
    }
    return lexiconItems;
  }

  @Override
  public List<BookItem> meCollectingBook(UserInfo userInfo) throws GlobalException {
    List<Book> books = bookMapper.selectLexiconByUserLexicon(userInfo.getId().longValue());
    List<BookItem> bookItems = new ArrayList<>();
    for (Book book : books) {
      BookItem bookItem = entityConvertModelUtils.convertBookToBookItem(book);
      bookItems.add(bookItem);
    }
    return bookItems;
  }
}
