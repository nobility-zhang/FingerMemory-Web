module.exports = class Book {
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
