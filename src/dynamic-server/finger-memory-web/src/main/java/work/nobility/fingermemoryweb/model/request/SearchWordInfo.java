package work.nobility.fingermemoryweb.model.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchWordInfo {
  private String word;
  private String category;
  private String prefix;
  private String suffix;
  private String contain;
}
