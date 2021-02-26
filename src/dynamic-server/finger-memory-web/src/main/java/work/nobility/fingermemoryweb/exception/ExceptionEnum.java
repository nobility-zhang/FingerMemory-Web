package work.nobility.fingermemoryweb.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ExceptionEnum {

  User_Name_Or_Password_Error(10001, "用户名或密码错误"),
  Not_Login(10002, "未登入，请先登入"),
  Username_Or_Email_Already_Exists(10002, "用户名名或邮箱已存在"),
  Original_Password_Error(10003, "原密码错误"),
  Delete_Error(10003, "删除错误"),
  Update_Error(10004, "更新错误"),
  Category_Not_Exists(10005, "类别不存在"),
  User_Not_Exists(10006, "用户不存在"),
  Repetitive_Operation_Error(10007, "重复操作错误"),

  System_Error(20000, "系统错误");

  Integer code;
  String message;
}
