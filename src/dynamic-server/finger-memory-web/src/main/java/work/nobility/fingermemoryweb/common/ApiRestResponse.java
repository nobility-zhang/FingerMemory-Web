package work.nobility.fingermemoryweb.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import work.nobility.fingermemoryweb.exception.ExceptionEnum;

@AllArgsConstructor
@Getter
@Setter
@ToString
public class ApiRestResponse<T> {
  private Integer status;
  private String massage;
  private T data;

  public static final int OK_CODE = 10000;
  public static final String OK_MASSAGE = "SUCCESS";

  public ApiRestResponse(Integer status, String massage) {
    this.status = status;
    this.massage = massage;
  }

  public ApiRestResponse() {
    this(OK_CODE, OK_MASSAGE);
  }

  public static <T> ApiRestResponse<T> success() {
    return new ApiRestResponse<>();
  }

  public static <T> ApiRestResponse<T> success(T data) {
    ApiRestResponse<T> apiRestResponse = new ApiRestResponse<>();
    apiRestResponse.setData(data);
    return apiRestResponse;
  }

  public static <T> ApiRestResponse<T> error(Integer code, String massage) {
    return new ApiRestResponse<>(code, massage);
  }

  public static <T> ApiRestResponse<T> error(ExceptionEnum exceptionEnum) {
    return new ApiRestResponse<>(exceptionEnum.getCode(), exceptionEnum.getMessage());
  }
}
