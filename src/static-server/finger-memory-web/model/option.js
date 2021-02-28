/**
 *  @swagger
 *  definitions:
 *    Option:
 *      properties:
 *        value:
 *          type: string
 *        reight:
 *          type: boolean
 */
module.exports = class Option {
    constructor(value, reight) {
        this.value = value;
        this.reight = reight;
    }
}