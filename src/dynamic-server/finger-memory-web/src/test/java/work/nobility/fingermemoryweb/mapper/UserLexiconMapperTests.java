package work.nobility.fingermemoryweb.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import work.nobility.fingermemoryweb.entity.Lexicon;
import work.nobility.fingermemoryweb.entity.User;
import work.nobility.fingermemoryweb.entity.UserLexicon;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class UserLexiconMapperTests {
  @Autowired
  UserLexiconMapper userLexiconMapper;
  @Autowired
  UserMapper userMapper;
  @Autowired
  LexiconMapper lexiconMapper;

  @Test
  public void insertOneLexiconWordMappingTest() {
    User user1 = new User();
    user1.setUserEmail("zhangsan1@example.com");
    user1.setUserPassword("123456");
    user1.setUserName("zhangsan1");
    user1.setUserAvatarUrl("https://picsum.photos/50/50");
    userMapper.insertOneUser(user1);
    User user2 = new User();
    user2.setUserEmail("zhangsan2@example.com");
    user2.setUserPassword("123456");
    user2.setUserName("zhangsan2");
    user2.setUserAvatarUrl("https://picsum.photos/50/50");
    userMapper.insertOneUser(user2);

    Lexicon lexicon = new Lexicon();
    lexicon.setLexiconAuthor(user1.getUserId());
    lexicon.setLexiconName("词库名");
    lexicon.setLexiconBaidescription("词库描述");
    lexicon.setLexiconCoverUrl("https://picsum.photos/400/400");
    lexiconMapper.insertOneLexicon(lexicon);

    UserLexicon userLexicon1 = new UserLexicon();
    userLexicon1.setLexiconId(lexicon.getLexiconId());
    userLexicon1.setUserId(user2.getUserId());

    userLexiconMapper.insertOneLexiconWordMapping(userLexicon1);

    UserLexicon userLexicon2 = userLexiconMapper
        .selectLexiconWordMappingListByUserId(user2.getUserId())
        .get(0);
    assertEquals(userLexicon1, userLexicon2);
    UserLexicon userLexicon3 = userLexiconMapper
        .selectLexiconWordMappingListByLexiconId(lexicon.getLexiconId())
        .get(0);
    assertEquals(userLexicon1, userLexicon3);

    userLexiconMapper.deleteLexiconWordMapping(userLexicon1);
    lexiconMapper.deleteLexiconById(lexicon.getLexiconId());
    userMapper.deleteUserById(user1.getUserId());
    userMapper.deleteUserById(user2.getUserId());
  }

  @Test
  public void updateLexiconWordMappingTest() {
    User user1 = new User();
    user1.setUserEmail("zhangsan1@example.com");
    user1.setUserPassword("123456");
    user1.setUserName("zhangsan1");
    user1.setUserAvatarUrl("https://picsum.photos/50/50");
    userMapper.insertOneUser(user1);
    User user2 = new User();
    user2.setUserEmail("zhangsan2@example.com");
    user2.setUserPassword("123456");
    user2.setUserName("zhangsan2");
    user2.setUserAvatarUrl("https://picsum.photos/50/50");
    userMapper.insertOneUser(user2);

    Lexicon lexicon1 = new Lexicon();
    lexicon1.setLexiconAuthor(user1.getUserId());
    lexicon1.setLexiconName("词库名1");
    lexicon1.setLexiconBaidescription("词库描述1");
    lexicon1.setLexiconCoverUrl("https://picsum.photos/400/400");
    lexiconMapper.insertOneLexicon(lexicon1);
    Lexicon lexicon2 = new Lexicon();
    lexicon2.setLexiconAuthor(user1.getUserId());
    lexicon2.setLexiconName("词库名2");
    lexicon2.setLexiconBaidescription("词库描述2");
    lexicon2.setLexiconCoverUrl("https://picsum.photos/400/400");
    lexiconMapper.insertOneLexicon(lexicon2);

    UserLexicon userLexicon1 = new UserLexicon();
    userLexicon1.setLexiconId(lexicon1.getLexiconId());
    userLexicon1.setUserId(user2.getUserId());
    userLexiconMapper.insertOneLexiconWordMapping(userLexicon1);
    UserLexicon userLexicon2 = new UserLexicon();
    userLexicon2.setLexiconId(lexicon2.getLexiconId());
    userLexicon2.setUserId(user2.getUserId());

    userLexiconMapper.updateLexiconWordMapping(userLexicon1, userLexicon2);

    UserLexicon userLexicon3 = userLexiconMapper
        .selectLexiconWordMappingListByUserId(user2.getUserId())
        .get(0);
    assertEquals(userLexicon2, userLexicon3);
    List<UserLexicon> userLexicons = userLexiconMapper
        .selectLexiconWordMappingListByLexiconId(lexicon1.getLexiconId());
    assertEquals(0, userLexicons.size());

    userLexiconMapper.deleteLexiconWordMapping(userLexicon2);
    lexiconMapper.deleteLexiconById(lexicon1.getLexiconId());
    lexiconMapper.deleteLexiconById(lexicon2.getLexiconId());
    userMapper.deleteUserById(user1.getUserId());
    userMapper.deleteUserById(user2.getUserId());
  }
}
