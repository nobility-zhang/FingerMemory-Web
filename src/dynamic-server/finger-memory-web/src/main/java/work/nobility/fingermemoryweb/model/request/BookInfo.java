package work.nobility.fingermemoryweb.model.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookInfo {
  private Integer id;
  private String category;
  private String coverUrl;
  private String name;
  private String description;
}
