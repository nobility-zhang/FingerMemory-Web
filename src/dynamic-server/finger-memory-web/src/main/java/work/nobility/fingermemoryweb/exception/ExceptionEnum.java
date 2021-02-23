package work.nobility.fingermemoryweb.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ExceptionEnum {

  User_Name_Or_Password_Error(10001, "用户名或密码错误"),
  Not_Login(10002, "未登入，请先登入"),

  System_Error(20000, "系统错误");

  Integer code;
  String message;
}
