package work.nobility.fingermemoryweb.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Word {
  private long wordId;
  private String wordEnglish;
  private String wordTranslate;
  private String wordBaidescription;
  private String wordCoverUrl;
  private long wordCategory;
}
