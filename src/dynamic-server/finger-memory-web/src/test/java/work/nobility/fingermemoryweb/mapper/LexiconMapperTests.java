package work.nobility.fingermemoryweb.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import work.nobility.fingermemoryweb.entity.Lexicon;
import work.nobility.fingermemoryweb.entity.User;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class LexiconMapperTests {
  @Autowired
  LexiconMapper lexiconMapper;
  @Autowired
  UserMapper userMapper;

  @Test
  public void insertOneLexiconTest() {
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

    Lexicon lexicon2 = lexiconMapper.selectLexiconById(lexicon1.getLexiconId());
    lexicon2.setLexiconCreateDate(null);

    assertEquals(lexicon1, lexicon2);
    lexiconMapper.deleteLexiconById(lexicon1.getLexiconId());
    userMapper.deleteUserById(user.getUserId());
  }

  @Test
  public void updateLexiconTest() {
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

    lexicon1.setLexiconBaidescription("词库描述update");
    lexiconMapper.updateLexicon(lexicon1);

    Lexicon lexicon2 = lexiconMapper.selectLexiconById(lexicon1.getLexiconId());
    lexicon2.setLexiconCreateDate(null);

    assertEquals(lexicon1, lexicon2);
    lexiconMapper.deleteLexiconById(lexicon1.getLexiconId());
    userMapper.deleteUserById(user.getUserId());
  }
}
