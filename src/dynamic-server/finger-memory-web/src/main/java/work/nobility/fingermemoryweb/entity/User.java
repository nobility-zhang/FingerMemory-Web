package work.nobility.fingermemoryweb.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
  private Long userId;
  private String userName;
  private String userEmail;
  private String userPassword;
  private java.sql.Timestamp userCreateDate;
  private String userAvatarUrl;
}
