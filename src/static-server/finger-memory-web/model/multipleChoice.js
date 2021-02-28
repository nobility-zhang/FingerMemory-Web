/**
 *  @swagger
 *  definitions:
 *    MultipleChoice:
 *      properties:
 *        id:
 *          type: string
 *        bookId:
 *          type: number
 *        issue:
 *          type: string
 *        resolve:
 *          type: string
 *        translated:
 *          type: string
 *        options:
 *          type: array
 *          items: 
 *            $ref: '#/definitions/Option'
 */
module.exports = class MultipleChoice {
    constructor(id, bookId, issue , translated, resolve, options) {
        this.id = id;
        this.bookId = bookId;
        this.issue = issue;
        this.translated = translated;
        this.resolve = resolve;
        this.options = options;
    }
}
