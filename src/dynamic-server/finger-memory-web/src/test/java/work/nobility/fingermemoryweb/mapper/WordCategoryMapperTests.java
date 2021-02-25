package work.nobility.fingermemoryweb.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import work.nobility.fingermemoryweb.entity.WordCategory;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class WordCategoryMapperTests {
  @Autowired
  WordCategoryMapper wordCategoryMapper;

  @Test
  public void insertOneWordCategoryTest() {
    WordCategory wordCategory1 = new WordCategory();
    wordCategory1.setCategoryName("分类1");

    wordCategoryMapper.insertOneWordCategory(wordCategory1);

    WordCategory wordCategory2 = wordCategoryMapper.selectWordCategoryById(wordCategory1.getCategoryId());

    assertEquals(wordCategory1, wordCategory2);

    wordCategoryMapper.deleteWordCategoryById(wordCategory1.getCategoryId());
  }

  @Test
  public void updateWordCategoryTest() {
    WordCategory wordCategory1 = new WordCategory();
    wordCategory1.setCategoryName("分类名");
    wordCategoryMapper.deleteWordCategoryById(wordCategory1.getCategoryId());

    wordCategoryMapper.insertOneWordCategory(wordCategory1);

    wordCategory1.setCategoryName("分类名update");
    wordCategoryMapper.updateWordCategory(wordCategory1);

    WordCategory wordCategory2 = wordCategoryMapper.selectWordCategoryById(wordCategory1.getCategoryId());

    assertEquals(wordCategory1, wordCategory2);

    wordCategoryMapper.deleteWordCategoryById(wordCategory1.getCategoryId());
  }
}
