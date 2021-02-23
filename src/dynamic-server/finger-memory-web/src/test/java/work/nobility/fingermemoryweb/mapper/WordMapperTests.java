package work.nobility.fingermemoryweb.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import work.nobility.fingermemoryweb.entity.User;
import work.nobility.fingermemoryweb.entity.Word;
import work.nobility.fingermemoryweb.entity.WordCategory;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class WordMapperTests {
  @Autowired
  WordMapper wordMapper;
  @Autowired
  WordCategoryMapper wordCategoryMapper;

  @Test
  public void insertOneWordTest() {
    WordCategory wordCategory = new WordCategory();
    wordCategory.setCategoryName("分类1");
    wordCategoryMapper.insertOneWordCategory(wordCategory);

    Word word1 = new Word();
    word1.setWordEnglish("english");
    word1.setWordTranslate("英语");
    word1.setWordBaidescription("单词描述单词描述单词描述");
    word1.setWordCoverUrl("https://picsum.photos/380/400");
    word1.setWordCategory(wordCategory.getCategoryId());

    wordMapper.insertOneWord(word1);

    Word word2 = wordMapper.selectWordById((int) word1.getWordId());

    assertEquals(word1, word2);
    wordMapper.deleteWordById((int) word1.getWordId());
    wordCategoryMapper.deleteWordCategoryById((int) wordCategory.getCategoryId());
  }

  @Test
  public void updateWordTest() {
    WordCategory wordCategory = new WordCategory();
    wordCategory.setCategoryName("分类1");
    wordCategoryMapper.insertOneWordCategory(wordCategory);

    Word word1 = new Word();
    word1.setWordEnglish("english");
    word1.setWordTranslate("英语");
    word1.setWordBaidescription("单词描述单词描述单词描述");
    word1.setWordCoverUrl("https://picsum.photos/380/400");
    word1.setWordCategory(wordCategory.getCategoryId());

    wordMapper.insertOneWord(word1);

    word1.setWordBaidescription("单词描述update");
    wordMapper.updateWord(word1);

    Word word2 = wordMapper.selectWordById((int) word1.getWordId());

    assertEquals(word1, word2);
    wordMapper.deleteWordById((int) word1.getWordId());
    wordCategoryMapper.deleteWordCategoryById((int) wordCategory.getCategoryId());
  }
}
