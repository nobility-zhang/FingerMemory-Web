package work.nobility.fingermemoryweb.mapper;
import org.apache.ibatis.annotations.*;
import work.nobility.fingermemoryweb.entity.Note;

@Mapper
public interface NoteMapper {
  @Insert("insert into " +
      "fm_notes(word_id, note_author, note_baidescription, note_create_date, note_tag)" +
      "value(#{wordId}, #{noteAuthor}, #{noteBaidescription}, current_timestamp, #{noteTag})")
  @SelectKey(
      keyProperty = "noteId",
      resultType = Integer.class,
      before = false,
      statement = "select last_insert_id()"
  )
  Integer insertOneNote(Note note);

  @Update("update fm_notes set " +
      "word_id = #{wordId}, " +
      "note_author = #{noteAuthor}, " +
      "note_baidescription = #{noteBaidescription}, " +
      "note_tag = #{noteTag}")
  Integer updateNote(Note note);

  @Select("select * from fm_notes where note_id = #{id}")
  Note selectNoteById(Integer id);

  @Delete("delete from fm_notes where note_id = #{id}")
  Integer deleteNoteById(Integer id);
}
