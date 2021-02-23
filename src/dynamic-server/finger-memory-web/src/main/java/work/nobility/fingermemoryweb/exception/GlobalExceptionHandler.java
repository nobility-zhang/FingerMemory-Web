package work.nobility.fingermemoryweb.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import work.nobility.fingermemoryweb.common.ApiRestResponse;

@ControllerAdvice
@RestController
@Slf4j
public class GlobalExceptionHandler {
  @ExceptionHandler(Exception.class)
  public Object handlerSystemException(Exception exception) {
    log.error("Default Exception: ", exception);
    return ApiRestResponse.error(ExceptionEnum.System_Error);
  }

  @ExceptionHandler(GlobalException.class)
  public Object handlerGlobalException(GlobalException exception) {
    log.error("Global Exception: ", exception);
    return ApiRestResponse.error(exception.getCode(), exception.getMessage());
  }
}
