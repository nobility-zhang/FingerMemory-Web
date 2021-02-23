package work.nobility.fingermemoryweb.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import work.nobility.fingermemoryweb.entity.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class LexiconWordMappingMapperTests {
  @Autowired
  LexiconWordMappingMapper lexiconWordMappingMapper;
  @Autowired
  UserMapper userMapper;
  @Autowired
  LexiconMapper lexiconMapper;
  @Autowired
  WordMapper wordMapper;
  @Autowired
  WordCategoryMapper wordCategoryMapper;

  @Test
  public void insertOneLexiconWordMappingTest() {
    User user = new User();
    user.setUserEmail("zhangsan@example.com");
    user.setUserPassword("123456");
    user.setUserName("zhangsan");
    user.setUserAvatarUrl("https://picsum.photos/50/50");
    userMapper.insertOneUser(user);

    Lexicon lexicon = new Lexicon();
    lexicon.setLexiconAuthor(user.getUserId());
    lexicon.setLexiconName("词库名");
    lexicon.setLexiconBaidescription("词库描述");
    lexicon.setLexiconCoverUrl("https://picsum.photos/400/400");
    lexiconMapper.insertOneLexicon(lexicon);

    WordCategory wordCategory = new WordCategory();
    wordCategory.setCategoryName("分类1");
    wordCategoryMapper.insertOneWordCategory(wordCategory);

    Word word = new Word();
    word.setWordEnglish("english");
    word.setWordTranslate("英语");
    word.setWordBaidescription("单词描述单词描述单词描述");
    word.setWordCoverUrl("https://picsum.photos/380/400");
    word.setWordCategory(wordCategory.getCategoryId());
    wordMapper.insertOneWord(word);

    LexiconWordMapping lexiconWordMapping1 = new LexiconWordMapping();
    lexiconWordMapping1.setLexiconId(lexicon.getLexiconId());
    lexiconWordMapping1.setWordId(word.getWordId());

    lexiconWordMappingMapper.insertOneLexiconWordMapping(lexiconWordMapping1);

    LexiconWordMapping lexiconWordMapping2 = lexiconWordMappingMapper
        .selectLexiconWordMappingListByWordId((int) word.getWordId())
        .get(0);
    assertEquals(lexiconWordMapping1, lexiconWordMapping2);
    LexiconWordMapping lexiconWordMapping3 = lexiconWordMappingMapper
        .selectLexiconWordMappingListByLexiconId((int) lexicon.getLexiconId())
        .get(0);
    assertEquals(lexiconWordMapping1, lexiconWordMapping3);

    lexiconWordMappingMapper.deleteLexiconWordMapping(lexiconWordMapping1);
    wordMapper.deleteWordById((int) word.getWordId());
    wordCategoryMapper.deleteWordCategoryById((int) wordCategory.getCategoryId());
    lexiconMapper.deleteLexiconById((int) lexicon.getLexiconId());
    userMapper.deleteUserById((int) user.getUserId());
  }

  @Test
  public void updateLexiconWordMappingTest() {
    User user = new User();
    user.setUserEmail("zhangsan@example.com");
    user.setUserPassword("123456");
    user.setUserName("zhangsan");
    user.setUserAvatarUrl("https://picsum.photos/50/50");
    userMapper.insertOneUser(user);

    Lexicon lexicon1 = new Lexicon();
    lexicon1.setLexiconAuthor(user.getUserId());
    lexicon1.setLexiconName("词库名");
    lexicon1.setLexiconBaidescription("词库描述");
    lexicon1.setLexiconCoverUrl("https://picsum.photos/400/400");
    lexiconMapper.insertOneLexicon(lexicon1);

    WordCategory wordCategory = new WordCategory();
    wordCategory.setCategoryName("分类1");
    wordCategoryMapper.insertOneWordCategory(wordCategory);
    Word word1 = new Word();
    word1.setWordEnglish("english");
    word1.setWordTranslate("英语");
    word1.setWordBaidescription("单词描述单词描述单词描述");
    word1.setWordCoverUrl("https://picsum.photos/380/400");
    word1.setWordCategory(wordCategory.getCategoryId());
    wordMapper.insertOneWord(word1);

    Lexicon lexicon2 = new Lexicon();
    lexicon2.setLexiconAuthor(user.getUserId());
    lexicon2.setLexiconName("词库名2");
    lexicon2.setLexiconBaidescription("词库描述2");
    lexicon2.setLexiconCoverUrl("https://picsum.photos/400/400");
    lexiconMapper.insertOneLexicon(lexicon2);

    Word word2 = new Word();
    word2.setWordEnglish("english2");
    word2.setWordTranslate("英语2");
    word2.setWordBaidescription("单词描述单词描述单词描述2");
    word2.setWordCoverUrl("https://picsum.photos/380/400");
    word2.setWordCategory(wordCategory.getCategoryId());
    wordMapper.insertOneWord(word2);

    LexiconWordMapping lexiconWordMapping1 = new LexiconWordMapping();
    lexiconWordMapping1.setLexiconId(lexicon1.getLexiconId());
    lexiconWordMapping1.setWordId(word1.getWordId());
    lexiconWordMappingMapper.insertOneLexiconWordMapping(lexiconWordMapping1);

    LexiconWordMapping lexiconWordMapping2 = new LexiconWordMapping();
    lexiconWordMapping2.setLexiconId(lexicon2.getLexiconId());
    lexiconWordMapping2.setWordId(word2.getWordId());

    lexiconWordMappingMapper.updateLexiconWordMapping(lexiconWordMapping1, lexiconWordMapping2);

    LexiconWordMapping lexiconWordMapping3 = lexiconWordMappingMapper
        .selectLexiconWordMappingListByWordId((int) word2.getWordId())
        .get(0);
    assertEquals(lexiconWordMapping2, lexiconWordMapping3);
    List<LexiconWordMapping> lexiconWordMappings = lexiconWordMappingMapper
        .selectLexiconWordMappingListByWordId((int) word1.getWordId());
    assertEquals(0,lexiconWordMappings.size());

    lexiconWordMappingMapper.deleteLexiconWordMapping(lexiconWordMapping1);
    wordMapper.deleteWordById((int) word1.getWordId());
    wordMapper.deleteWordById((int) word2.getWordId());
    wordCategoryMapper.deleteWordCategoryById((int) wordCategory.getCategoryId());
    lexiconMapper.deleteLexiconById((int) lexicon1.getLexiconId());
    lexiconMapper.deleteLexiconById((int) lexicon2.getLexiconId());
    userMapper.deleteUserById((int) user.getUserId());
  }
}
