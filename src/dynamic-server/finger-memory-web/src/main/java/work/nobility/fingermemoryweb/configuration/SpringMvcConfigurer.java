package work.nobility.fingermemoryweb.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import work.nobility.fingermemoryweb.interceptor.LoginInterceptor;
import work.nobility.fingermemoryweb.utils.RedisHttpSession;

@Configuration
public class SpringMvcConfigurer implements WebMvcConfigurer {
  @Autowired
  private RedisHttpSession redisHttpSession;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new LoginInterceptor(redisHttpSession))
        .addPathPatterns(
            "/me",
            "/me-lexicon",
            "/me-collecting-lexicon",
            "/me-collecting-book"
        )
        .excludePathPatterns();
  }
}
