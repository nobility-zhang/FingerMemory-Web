package work.nobility.fingermemoryweb.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import work.nobility.fingermemoryweb.entity.User;
import work.nobility.fingermemoryweb.exception.ExceptionEnum;
import work.nobility.fingermemoryweb.exception.GlobalException;
import work.nobility.fingermemoryweb.mapper.UserMapper;
import work.nobility.fingermemoryweb.model.request.ChangePasswordInfo;
import work.nobility.fingermemoryweb.model.request.UserRegisterInfo;
import work.nobility.fingermemoryweb.service.SignUpService;

@Service
public class SignUpServiceImpl implements SignUpService {
  @Autowired
  UserMapper userMapper;

  @Override
  public void signUp(UserRegisterInfo userRegisterInfo) throws GlobalException {
    User user = new User();
    user.setUserName(userRegisterInfo.getUserName());
    user.setUserEmail(userRegisterInfo.getUserEmail());
    user.setUserAvatarUrl(userRegisterInfo.getUserAvatarUrl());
    user.setUserPassword(userRegisterInfo.getUserPassword());
    try {
      userMapper.insertOneUser(user);
    } catch (Exception e) {
      throw new GlobalException(ExceptionEnum.Username_Or_Email_Already_Exists);
    }
  }

  @Override
  public void changePassword(ChangePasswordInfo changePasswordInfo, Integer id) throws GlobalException {
    User user = userMapper.selectUserById(id.longValue());
    if (user.getUserPassword().equals(changePasswordInfo.getOriginalPassword())) {
      user.setUserPassword(changePasswordInfo.getNewPassword());
      userMapper.updateUser(user);
    } else {
      throw new GlobalException(ExceptionEnum.Original_Password_Error);
    }
  }
}
