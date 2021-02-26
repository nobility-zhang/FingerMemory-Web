package work.nobility.fingermemoryweb.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import work.nobility.fingermemoryweb.entity.*;
import work.nobility.fingermemoryweb.exception.ExceptionEnum;
import work.nobility.fingermemoryweb.exception.GlobalException;
import work.nobility.fingermemoryweb.mapper.LexiconMapper;
import work.nobility.fingermemoryweb.mapper.UserLexiconMapper;
import work.nobility.fingermemoryweb.mapper.UserMapper;
import work.nobility.fingermemoryweb.model.request.LexiconInfo;
import work.nobility.fingermemoryweb.model.request.SearchLexiconInfo;
import work.nobility.fingermemoryweb.model.response.LexiconItem;
import work.nobility.fingermemoryweb.service.LexiconService;
import work.nobility.fingermemoryweb.utils.EntityConvertModelUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class LexiconServiceImpl implements LexiconService {
  @Autowired
  LexiconMapper lexiconMapper;
  @Autowired
  UserMapper userMapper;
  @Autowired
  UserLexiconMapper userLexiconMapper;
  @Autowired
  EntityConvertModelUtils entityConvertModelUtils;

  @Override
  public List<LexiconItem> lexiconList(SearchLexiconInfo searchLexiconInfo) {
    String authorName = searchLexiconInfo.getAuthorName();
    User user;
    if (authorName == null) {
      user = new User();
      user.setUserId(null);
    } else {
      user = userMapper.selectUserByName(authorName);
    }

    List<Lexicon> lexicons = lexiconMapper.selectLexicon(
        searchLexiconInfo.getName(),
        user.getUserId(),
        searchLexiconInfo.getPrefix(),
        searchLexiconInfo.getSuffix(),
        searchLexiconInfo.getContain()
    );
    List<LexiconItem> lexiconItems = new ArrayList<>();
    for (Lexicon lexicon : lexicons) {
      LexiconItem lexiconItem = entityConvertModelUtils.convertLexiconToLexiconItem(lexicon);
      lexiconItems.add(lexiconItem);
    }
    return lexiconItems;
  }

  @Override
  public void addLexicon(LexiconInfo lexiconInfo, Integer authorId) {
    Lexicon lexicon = entityConvertModelUtils.convertLexiconInfoToLexicon(lexiconInfo, authorId.longValue());
    lexiconMapper.insertOneLexicon(lexicon);
  }

  @Override
  public void updateLexicon(LexiconInfo lexiconInfo, Integer authorId) throws GlobalException {
    Lexicon lexicon = lexiconMapper.selectLexiconById(lexiconInfo.getId().longValue());
    if (lexicon.getLexiconAuthor().equals(authorId.longValue())) {
      lexicon = entityConvertModelUtils.convertLexiconInfoToLexicon(lexiconInfo, authorId.longValue());
      lexicon.setLexiconId(lexiconInfo.getId().longValue());
      lexiconMapper.updateLexicon(lexicon);
    } else {
      throw new GlobalException(ExceptionEnum.Update_Error);
    }
  }

  @Override
  public void deleteLexicon(Integer id, Integer authorId) throws GlobalException {
    Lexicon lexicon = lexiconMapper.selectLexiconById(id.longValue());
    if (lexicon.getLexiconAuthor().equals(authorId.longValue())) {
      lexiconMapper.deleteLexiconById(id.longValue());
    } else {
      throw new GlobalException(ExceptionEnum.Delete_Error);
    }
  }

  @Override
  public void collectingLexicon(Integer id, Integer authorId) throws GlobalException {
    UserLexicon userLexicon = new UserLexicon(
        authorId.longValue(),
        id.longValue()
    );
    if (userLexiconMapper.selectLexicon(userLexicon) == null) {
      userLexiconMapper.insertOneLexiconWordMapping(userLexicon);
    } else {
      throw new GlobalException(ExceptionEnum.Repetitive_Operation_Error);
    }
  }

  @Override
  public void cancelCollectingLexicon(Integer id, Integer authorId) {
    UserLexicon userLexicon = new UserLexicon(
        authorId.longValue(),
        id.longValue()
    );
    userLexiconMapper.deleteLexiconWordMapping(userLexicon);
  }
}
