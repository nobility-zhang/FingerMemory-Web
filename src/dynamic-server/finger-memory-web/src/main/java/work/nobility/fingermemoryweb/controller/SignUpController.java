package work.nobility.fingermemoryweb.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import work.nobility.fingermemoryweb.common.ApiRestResponse;
import work.nobility.fingermemoryweb.common.Constant;
import work.nobility.fingermemoryweb.configuration.SessionUtilsConfig;
import work.nobility.fingermemoryweb.exception.GlobalException;
import work.nobility.fingermemoryweb.model.request.ChangePasswordInfo;
import work.nobility.fingermemoryweb.model.request.UserRegisterInfo;
import work.nobility.fingermemoryweb.model.response.UserInfo;
import work.nobility.fingermemoryweb.service.SignUpService;
import work.nobility.fingermemoryweb.utils.RedisHttpSession;

import javax.servlet.http.HttpSession;


@RestController
public class SignUpController {
  @Autowired
  SignUpService signUpService;
  @Autowired
  private SessionUtilsConfig sessionUtilsConfig;

  @PostMapping("/sign-up")
  public ApiRestResponse<Object> signUp(@RequestBody UserRegisterInfo userRegisterInfo)
      throws GlobalException {
    signUpService.signUp(userRegisterInfo);
    return ApiRestResponse.success();
  }

  @PostMapping("/change-password")
  public ApiRestResponse<Object> changePassword(@RequestBody ChangePasswordInfo changePasswordInfo,
                                                HttpSession session) throws GlobalException {
    RedisHttpSession redisHttpSession = sessionUtilsConfig.getRedisHttpSessionBean();
    redisHttpSession.setSession(session);
    UserInfo userInfo = redisHttpSession.getAttribute(Constant.UID, UserInfo.class);
    signUpService.changePassword(changePasswordInfo, userInfo.getId());
    return ApiRestResponse.success();
  }
}
