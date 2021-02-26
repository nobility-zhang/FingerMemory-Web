package work.nobility.fingermemoryweb.model.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WordInfo {
  private Integer id;
  private String category;
  private String english;
  private String translate;
  private String description;
  private String coverUrl;
}
