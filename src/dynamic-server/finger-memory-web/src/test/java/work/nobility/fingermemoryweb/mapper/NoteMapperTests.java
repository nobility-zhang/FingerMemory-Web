package work.nobility.fingermemoryweb.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import work.nobility.fingermemoryweb.entity.Note;
import work.nobility.fingermemoryweb.entity.User;
import work.nobility.fingermemoryweb.entity.Word;
import work.nobility.fingermemoryweb.entity.WordCategory;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class NoteMapperTests {
  @Autowired
  NoteMapper noteMapper;
  @Autowired
  WordMapper wordMapper;
  @Autowired
  WordCategoryMapper wordCategoryMapper;
  @Autowired
  UserMapper userMapper;

  @Test
  public void insertOneNoteTest() {
    User user = new User();
    user.setUserEmail("zhangsan@example.com");
    user.setUserPassword("123456");
    user.setUserName("zhangsan");
    user.setUserAvatarUrl("https://picsum.photos/50/50");
    userMapper.insertOneUser(user);

    WordCategory wordCategory = new WordCategory();
    wordCategory.setCategoryName("分类1");
    wordCategoryMapper.insertOneWordCategory(wordCategory);

    Word word = new Word();
    word.setWordEnglish("english");
    word.setWordTranslate("英语");
    word.setWordBaidescription("单词描述单词描述单词描述");
    word.setWordCoverUrl("https://picsum.photos/380/400");
    word.setWordCategory(wordCategory.getCategoryId());
    wordMapper.insertOneWord(word);

    Note note1 = new Note();
    note1.setNoteAuthor(user.getUserId());
    note1.setWordId(word.getWordId());

    noteMapper.insertOneNote(note1);

    Note note2 = noteMapper.selectNoteById((int) note1.getNoteId());
    note2.setNoteCreateDate(null);

    assertEquals(note1, note2);

    noteMapper.deleteNoteById((int) note1.getNoteId());
    wordMapper.deleteWordById((int) word.getWordId());
    wordCategoryMapper.deleteWordCategoryById((int) wordCategory.getCategoryId());
    userMapper.deleteUserById((int) user.getUserId());
  }

  @Test
  public void updateNoteTest() {
    User user = new User();
    user.setUserEmail("zhangsan@example.com");
    user.setUserPassword("123456");
    user.setUserName("zhangsan");
    user.setUserAvatarUrl("https://picsum.photos/50/50");
    userMapper.insertOneUser(user);

    WordCategory wordCategory = new WordCategory();
    wordCategory.setCategoryName("分类1");
    wordCategoryMapper.insertOneWordCategory(wordCategory);

    Word word = new Word();
    word.setWordEnglish("english");
    word.setWordTranslate("英语");
    word.setWordBaidescription("单词描述单词描述单词描述");
    word.setWordCoverUrl("https://picsum.photos/380/400");
    word.setWordCategory(wordCategory.getCategoryId());
    wordMapper.insertOneWord(word);

    Note note1 = new Note();
    note1.setNoteAuthor(user.getUserId());
    note1.setWordId(word.getWordId());
    noteMapper.insertOneNote(note1);

    note1.setNoteBaidescription("笔记内容");
    noteMapper.updateNote(note1);

    Note note2 = noteMapper.selectNoteById((int) note1.getNoteId());
    note2.setNoteCreateDate(null);

    assertEquals(note1, note2);

    noteMapper.deleteNoteById((int) note1.getNoteId());
    wordMapper.deleteWordById((int) word.getWordId());
    wordCategoryMapper.deleteWordCategoryById((int) wordCategory.getCategoryId());
    userMapper.deleteUserById((int) user.getUserId());
  }
}
