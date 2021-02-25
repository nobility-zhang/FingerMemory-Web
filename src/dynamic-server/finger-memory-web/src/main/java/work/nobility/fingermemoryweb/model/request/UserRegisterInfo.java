package work.nobility.fingermemoryweb.model.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisterInfo {
  private String userName;
  private String userEmail;
  private String userPassword;
  private String userAvatarUrl;
}
