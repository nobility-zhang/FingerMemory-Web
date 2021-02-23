package work.nobility.fingermemoryweb.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class GlobalException extends Exception {
  public final Integer code;
  public final String message;

  public GlobalException(ExceptionEnum exceptionEnum) {
    this(exceptionEnum.getCode(), exceptionEnum.getMessage());
  }
}
