package work.nobility.fingermemoryweb.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Lexicon {
  private long lexiconId;
  private String lexiconName;
  private long lexiconAuthor;
  private String lexiconBaidescription;
  private java.sql.Timestamp lexiconCreateDate;
  private String lexiconCoverUrl;
}
