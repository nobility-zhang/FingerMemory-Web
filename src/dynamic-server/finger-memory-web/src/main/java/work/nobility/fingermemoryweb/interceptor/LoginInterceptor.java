package work.nobility.fingermemoryweb.interceptor;

import org.springframework.web.servlet.HandlerInterceptor;
import work.nobility.fingermemoryweb.common.Constant;
import work.nobility.fingermemoryweb.exception.ExceptionEnum;
import work.nobility.fingermemoryweb.exception.GlobalException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginInterceptor implements HandlerInterceptor {
  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    HttpSession session = request.getSession();
    Object uid = session.getAttribute(Constant.UID);
    if (uid == null) {
      throw new GlobalException(ExceptionEnum.Not_Login);
    }
    return true;
  }
}
