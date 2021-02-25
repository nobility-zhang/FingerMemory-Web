package work.nobility.fingermemoryweb.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import work.nobility.fingermemoryweb.common.ApiRestResponse;
import work.nobility.fingermemoryweb.exception.GlobalException;
import work.nobility.fingermemoryweb.model.request.UserRegisterInfo;
import work.nobility.fingermemoryweb.service.SignUpService;


@RestController
public class SignUpController {
  @Autowired
  SignUpService signUpService;

  @PostMapping("/sign-up")
  public ApiRestResponse<Object> signUp(@RequestBody UserRegisterInfo userRegisterInfo)
      throws GlobalException {
    signUpService.signUp(userRegisterInfo);
    return ApiRestResponse.success();
  }
}
