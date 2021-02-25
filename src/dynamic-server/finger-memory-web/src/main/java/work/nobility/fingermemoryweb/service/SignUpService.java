package work.nobility.fingermemoryweb.service;

import work.nobility.fingermemoryweb.exception.GlobalException;
import work.nobility.fingermemoryweb.model.request.UserRegisterInfo;

public interface SignUpService {
  void signUp(UserRegisterInfo userRegisterInfo) throws GlobalException;
}
