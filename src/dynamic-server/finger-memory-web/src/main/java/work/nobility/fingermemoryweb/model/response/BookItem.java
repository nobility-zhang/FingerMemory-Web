package work.nobility.fingermemoryweb.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookItem {
  private Integer id;
  private String coverUrl;
  private String name;
  private String description;
  private String author;
}
