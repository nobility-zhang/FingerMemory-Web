package work.nobility.fingermemoryweb.service;

import work.nobility.fingermemoryweb.exception.GlobalException;
import work.nobility.fingermemoryweb.model.response.UserInfo;

public interface LoginService {
  UserInfo login(String account, String password) throws GlobalException;
}
