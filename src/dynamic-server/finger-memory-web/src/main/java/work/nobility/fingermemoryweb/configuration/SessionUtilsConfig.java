package work.nobility.fingermemoryweb.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import work.nobility.fingermemoryweb.utils.RedisHttpSession;


@Configuration(proxyBeanMethods = false)

public class SessionUtilsConfig {
  @Autowired
  private StringRedisTemplate stringRedisTemplate;
  @Value("${fm.session.max-age}")
  private Integer maxAge;
  @Bean
  public RedisHttpSession getRedisHttpSessionBean() {
    RedisHttpSession redisHttpSession = new RedisHttpSession(stringRedisTemplate);
    redisHttpSession.setMaxAge(maxAge);
    return redisHttpSession;
  }
}
