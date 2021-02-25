package work.nobility.fingermemoryweb.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Note {
  private Long noteId;
  private Long wordId;
  private Long noteAuthor;
  private String noteBaidescription;
  private java.sql.Timestamp noteCreateDate;
  private String noteTag;
}
