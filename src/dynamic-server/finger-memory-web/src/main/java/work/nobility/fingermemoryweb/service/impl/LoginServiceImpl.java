package work.nobility.fingermemoryweb.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import work.nobility.fingermemoryweb.entity.User;
import work.nobility.fingermemoryweb.exception.ExceptionEnum;
import work.nobility.fingermemoryweb.exception.GlobalException;
import work.nobility.fingermemoryweb.mapper.UserMapper;
import work.nobility.fingermemoryweb.model.response.UserInfo;
import work.nobility.fingermemoryweb.service.LoginService;

@Service
public class LoginServiceImpl implements LoginService {
  @Autowired
  UserMapper userMapper;

  @Override
  public UserInfo login(String account, String password) throws GlobalException {
    User user = userMapper.selectUserByAuth(account, password);
    if (user == null) {
      throw new GlobalException(ExceptionEnum.User_Name_Or_Password_Error);
    }
    UserInfo userInfo = new UserInfo();
    userInfo.setId((int) user.getUserId().longValue());
    userInfo.setUserName(user.getUserName());
    userInfo.setUserEmail(user.getUserEmail());
    userInfo.setUserAvatarUrl(user.getUserAvatarUrl());
    return userInfo;
  }
}
