package work.nobility.fingermemoryweb.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import work.nobility.fingermemoryweb.common.ApiRestResponse;
import work.nobility.fingermemoryweb.common.Constant;
import work.nobility.fingermemoryweb.entity.User;
import work.nobility.fingermemoryweb.exception.ExceptionEnum;
import work.nobility.fingermemoryweb.model.request.UserLoginAuthentication;
import work.nobility.fingermemoryweb.model.response.UserInfo;
import work.nobility.fingermemoryweb.utils.RedisHttpSession;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.net.http.HttpRequest;
import java.util.Map;

@Slf4j
@RestController
public class LoginController {
  @Autowired
  RedisHttpSession redisHttpSession;

  @PostMapping("/login")
  public ApiRestResponse login(@RequestBody UserLoginAuthentication user,
                               HttpSession session) {
    String account = user.getAccount();
    String password = user.getPassword();
    if (account.equals("zhangsan") && password.equals("123")) {
      redisHttpSession.setSession(session);
      redisHttpSession.setAttribute(Constant.UID, user);
      UserInfo userInfo = new UserInfo();
      userInfo.setUserName(user.getAccount());
      return ApiRestResponse.success(userInfo);
    }
    return ApiRestResponse.error(ExceptionEnum.User_Name_Or_Password_Error);
  }

  @PostMapping("/logout")
  public ApiRestResponse logout(HttpSession session) {
    redisHttpSession.removeAttribute(Constant.UID);
    redisHttpSession.deleteSession(session);
    return ApiRestResponse.success();
  }

  @GetMapping("/me")
  public ApiRestResponse test() {
    return ApiRestResponse.success("me");
  }
}
