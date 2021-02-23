package work.nobility.fingermemoryweb.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import work.nobility.fingermemoryweb.entity.Book;
import work.nobility.fingermemoryweb.entity.BookCategory;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookAndBookCategory {
  Book book;
  BookCategory bookCategory;
}
