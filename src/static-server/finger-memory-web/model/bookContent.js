/**
 *  @swagger
 *  definitions:
 *    BookContent:
 *      properties:
 *        id:
 *          type: number
 *        englishTitle:
 *          type: string
 *        translateTitle:
 *          type: string
 *        englishContent:
 *          type: string
 *        translateContent:
 *          type: string
 *        mark:
 *          type: string
 *        note:
 *          type: string
 */
module.exports = class BookContent {
    constructor(id, englishTitle , translateTitle, englishContent, translateContent, mark, note) {
        this.id = id;
        this.englishTitle = englishTitle;
        this.translateTitle = translateTitle;
        this.englishContent = englishContent;
        this.translateContent = translateContent;
        this.mark = mark;
        this.note = note;
    }
    setMark(mark) {
        this.mark = mark;
    }
    getMark() {
        return mark;
    }
    setNote(note) {
        this.note = note;
    }
    getNote() {
        return note;
    }
}
