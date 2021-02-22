package work.nobility.fingermemoryweb.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Book {
  private long bookId;
  private String bookName;
  private long bookAuthor;
  private long bookCategory;
  private String bookBaidescription;
  private String bookCoverUrl;
  private java.sql.Timestamp bookCreateDate;
}
