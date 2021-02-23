package work.nobility.fingermemoryweb.model.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginAuthentication {
  private String account;
  private String password;
  private String kaptcha;
}
