package work.nobility.fingermemoryweb.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.servlet.http.HttpSession;
import java.util.concurrent.TimeUnit;


public class RedisHttpSession {
  private HttpSession session;
  private Integer mexAge;

  private final StringRedisTemplate stringRedisTemplate;
  private final HashOperations<String, String, String> hashOperations;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public RedisHttpSession(StringRedisTemplate stringRedisTemplate) {
    this.stringRedisTemplate = stringRedisTemplate;
    this.hashOperations = this.stringRedisTemplate.opsForHash();
  }

  public void setSession(HttpSession session) {
    this.session = session;
  }

  public void deleteSession(HttpSession session) {
    stringRedisTemplate.delete(session.getId());
    this.session = null;
  }

  public <T> T getAttribute(String name, Class<T> clazz) {
    String jsonString = hashOperations.get(session.getId(), name);
    T value = null;
    try {
      value = objectMapper.readValue(jsonString, clazz);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    return value;
  }

  public String getAttribute(String name) {
    return hashOperations.get(session.getId(), name);
  }

  public void setAttribute(String name, Object value) {
    String jsonString;
    try {
      jsonString = objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
      return;
    }
    hashOperations.put(session.getId(), name, jsonString);
    stringRedisTemplate.expire(session.getId(), this.mexAge, TimeUnit.SECONDS);
  }

  public void removeAttribute(String name) {
    hashOperations.delete(session.getId(), name);
  }

  public void setMaxAge(Integer mexAge) {
    this.mexAge = mexAge;
  }
}
