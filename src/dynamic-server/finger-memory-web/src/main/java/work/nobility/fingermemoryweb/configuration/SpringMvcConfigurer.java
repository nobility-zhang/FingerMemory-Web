package work.nobility.fingermemoryweb.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import work.nobility.fingermemoryweb.interceptor.LoginInterceptor;
import work.nobility.fingermemoryweb.utils.RedisHttpSession;

@Configuration
public class SpringMvcConfigurer implements WebMvcConfigurer {
  @Autowired
  private RedisHttpSession redisHttpSession;
  @Value("${fm.origin}")
  private String originUrl;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new LoginInterceptor(redisHttpSession))
        .addPathPatterns(
            "/change-password",
            "/me-lexicon",
            "/me-collecting-lexicon",
            "/me-collecting-book",
            "/book",
            "/collecting-book",
            "/lexicon",
            "/collecting-lexicon",
            "/word",
            "/collecting-word"
        )
        .excludePathPatterns();
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**")
        .allowedOrigins(originUrl)
        .allowCredentials(true)
        .allowedMethods("*");
  }
}
