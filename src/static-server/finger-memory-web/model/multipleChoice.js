/**
 *  @swagger
 *  definitions:
 *    MultipleChoice:
 *      properties:
 *        id:
 *          type: number
 *        issue:
 *          type: string
 *        translated:
 *          type: string
 *        options:
 *          type: array
 *          items: 
 *            $ref: '#/definitions/Option'
 */
module.exports = class MultipleChoice {
    constructor(id, issue , translated, resolve, options) {
        this.id = id;
        this.issue = issue;
        this.translated = translated;
        this.resolve = resolve;
        this.options = options;
    }
}
