package work.nobility.fingermemoryweb.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WordCategory {
  private long categoryId;
  private String categoryName;
}
