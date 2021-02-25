package work.nobility.fingermemoryweb.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WordCard {
  private Integer id;
  private String imageUrl;
  private String english;
  private String translation;
  private String description;
  private String note;
}
