package work.nobility.fingermemoryweb.mapper;
import org.apache.ibatis.annotations.*;
import work.nobility.fingermemoryweb.entity.Note;

import java.util.List;

@Mapper
public interface NoteMapper {
  @Insert("insert into " +
      "fm_notes(word_id, note_author, note_baidescription, note_create_date, note_tag)" +
      "value(#{wordId}, #{noteAuthor}, #{noteBaidescription}, current_timestamp, #{noteTag})")
  @SelectKey(
      keyProperty = "noteId",
      resultType = Long.class,
      before = false,
      statement = "select last_insert_id()"
  )
  Integer insertOneNote(Note note);

  @Update("update fm_notes set " +
      "word_id = #{wordId}, " +
      "note_author = #{noteAuthor}, " +
      "note_baidescription = #{noteBaidescription}, " +
      "note_tag = #{noteTag} " +
      "where note_id = #{noteId}")
  Integer updateNote(Note note);

  @Select("select * from fm_notes where note_id = #{id}")
  Note selectNoteById(Long id);

  @Delete("delete from fm_notes where note_id = #{id}")
  Integer deleteNoteById(Long id);

  @Select("select * from fm_notes where word_id = #{wordId} and note_author = #{noteAuthorId}")
  List<Note> selectNoteByWordIdAndNoteAuthorId(@Param("wordId") Long wordId,
                                               @Param("wordId") Long noteAuthorId);

  @Select("select * from fm_notes where word_id = #{id} order by rand() limit 0,1")
  Note selectNoteByWordIdRandomNoteAuthorId(Long id);
}
