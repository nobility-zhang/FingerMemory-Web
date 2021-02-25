package work.nobility.fingermemoryweb.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import work.nobility.fingermemoryweb.entity.User;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class UserMapperTests {
  @Autowired
  UserMapper userMapper;
  @Test
  public void insertOneUserTest() {
    User user1 = new User();
    user1.setUserEmail("zhangsan@example.com");
    user1.setUserPassword("123456");
    user1.setUserName("zhangsan");
    user1.setUserAvatarUrl("https://picsum.photos/50/50");

    userMapper.insertOneUser(user1);

    User user2 = userMapper.selectUserById( user1.getUserId());
    user2.setUserCreateDate(null);

    assertEquals(user1, user2);

    userMapper.deleteUserById( user1.getUserId());
  }

  @Test
  public void updateUserTest() {
    User user1 = new User();
    user1.setUserEmail("zhangsan@example.com");
    user1.setUserPassword("123456");
    user1.setUserName("zhangsan");
    user1.setUserAvatarUrl("https://picsum.photos/50/50");

    userMapper.insertOneUser(user1);

    user1.setUserPassword("123");
    userMapper.updateUser(user1);

    User user2 = userMapper.selectUserById( user1.getUserId());
    user2.setUserCreateDate(null);

    assertEquals(user1, user2);

    userMapper.deleteUserById( user1.getUserId());
  }
}
