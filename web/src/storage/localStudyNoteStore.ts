import type { StudyNote } from '../core/domain';
import type { SaveStudyNoteInput } from '../core/repository';
import { StudyItemNotFoundError } from '../core/repository';
import type { FingerMemoryDatabase } from './database';
import {
  assertRepositoryIdentifier,
  assertRepositoryTimestamp,
} from './learningRecordInputs';

export class LocalStudyNoteStore {
  constructor(private readonly database: FingerMemoryDatabase) {}

  async listStudyNotes(): Promise<StudyNote[]> {
    await this.database.open();
    const notes = await this.database.notes.toArray();
    return notes.sort(
      (left, right) =>
        left.studyItemId.localeCompare(right.studyItemId) ||
        right.updatedAt - left.updatedAt ||
        left.id.localeCompare(right.id),
    );
  }

  async saveStudyNote(
    input: SaveStudyNoteInput,
  ): Promise<StudyNote | undefined> {
    assertRepositoryIdentifier(input.studyItemId, 'study item id');
    assertRepositoryTimestamp(input.now, 'study note update time');
    await this.database.open();

    return this.database.transaction(
      'rw',
      [this.database.studyItems, this.database.notes],
      async () => {
        if (
          (await this.database.studyItems.get(input.studyItemId)) ===
          undefined
        ) {
          throw new StudyItemNotFoundError(input.studyItemId);
        }
        const existing = await this.database.notes
          .where('studyItemId')
          .equals(input.studyItemId)
          .first();
        if (input.content.trim().length === 0) {
          if (existing !== undefined) {
            await this.database.notes.delete(existing.id);
          }
          return undefined;
        }

        const note: StudyNote = {
          id: existing?.id ?? `note:${input.studyItemId}`,
          studyItemId: input.studyItemId,
          content: input.content,
          createdAt: existing?.createdAt ?? input.now,
          updatedAt: input.now,
        };
        await this.database.notes.put(note);
        return note;
      },
    );
  }
}
