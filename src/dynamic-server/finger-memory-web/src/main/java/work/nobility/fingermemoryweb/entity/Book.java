package work.nobility.fingermemoryweb.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Book {
  private Long bookId;
  private String bookName;
  private Long bookAuthor;
  private Long bookCategory;
  private String bookBaidescription;
  private String bookCoverUrl;
  private java.sql.Timestamp bookCreateDate;
}
