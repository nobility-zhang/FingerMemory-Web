package work.nobility.fingermemoryweb.model.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchBookInfo {
  private String name;
  private String category;
  private String prefix;
  private String suffix;
  private String contain;
}
