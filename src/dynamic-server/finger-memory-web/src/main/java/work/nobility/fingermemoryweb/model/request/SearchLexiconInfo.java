package work.nobility.fingermemoryweb.model.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchLexiconInfo {
  private String name;
  private String prefix;
  private String suffix;
  private String contain;
  private String authorName;
}
