package work.nobility.fingermemoryweb.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WordItem {
  private Integer id;
  private String english;
  private String translation;
}
