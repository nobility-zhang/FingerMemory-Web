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
import work.nobility.fingermemoryweb.exception.GlobalException;
import work.nobility.fingermemoryweb.model.request.UserLoginAuthentication;
import work.nobility.fingermemoryweb.model.response.UserInfo;
import work.nobility.fingermemoryweb.service.LoginService;
import work.nobility.fingermemoryweb.utils.RedisHttpSession;

import javax.servlet.http.HttpSession;

@Slf4j
@RestController
public class LoginController {
  @Autowired
  private RedisHttpSession redisHttpSession;
  @Autowired
  private LoginService loginService;

  @PostMapping("/login")
  public ApiRestResponse<UserInfo> login(@RequestBody UserLoginAuthentication userLoginAuthentication,
                                         HttpSession session) throws GlobalException {
    UserInfo userInfo = loginService.login(
        userLoginAuthentication.getAccount(),
        userLoginAuthentication.getPassword()
    );

    redisHttpSession.setSession(session);
    redisHttpSession.setAttribute(Constant.UID, userInfo);
    return ApiRestResponse.success(userInfo);
  }

  @PostMapping("/logout")
  public ApiRestResponse<Object> logout(HttpSession session) {
    redisHttpSession.removeAttribute(Constant.UID);
    redisHttpSession.deleteSession(session);
    return ApiRestResponse.success();
  }

  @GetMapping("/me")
  public ApiRestResponse<String> test() {
    return ApiRestResponse.success("me");
  }
}
