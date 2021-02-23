package work.nobility.fingermemoryweb.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo {
  private String userName;
  private String userEmail;
  private String userAvatarUrl;
}
