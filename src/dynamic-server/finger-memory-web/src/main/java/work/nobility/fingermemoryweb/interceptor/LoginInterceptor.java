package work.nobility.fingermemoryweb.interceptor;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.web.servlet.HandlerInterceptor;
import work.nobility.fingermemoryweb.common.Constant;
import work.nobility.fingermemoryweb.exception.ExceptionEnum;
import work.nobility.fingermemoryweb.exception.GlobalException;
import work.nobility.fingermemoryweb.utils.RedisHttpSession;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@NoArgsConstructor
@AllArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {
  private RedisHttpSession redisHttpSession;

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    HttpSession session = request.getSession();
    redisHttpSession.setSession(session);
    String uid = redisHttpSession.getAttribute(Constant.UID);
    if (uid == null) {
      throw new GlobalException(ExceptionEnum.Not_Login);
    }
    return true;
  }
}
