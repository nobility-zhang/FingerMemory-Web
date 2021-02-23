package work.nobility.fingermemoryweb.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import work.nobility.fingermemoryweb.interceptor.LoginInterceptor;

@Configuration
public class SpringMvcConfigurer implements WebMvcConfigurer {
  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new LoginInterceptor())
        .addPathPatterns("/me")
        .excludePathPatterns();
  }
}
