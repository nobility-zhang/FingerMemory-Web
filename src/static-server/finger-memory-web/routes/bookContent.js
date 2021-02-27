const router = require('koa-router')()
const { bookContentService } = require("../service/bookContentService.js")
const ApiRestResponse = require("../common/apiRestResponse.js")
/**
 * @swagger
 * /book-content:
 *   get:
 *     tags: [book content controller]
 *     produces:
 *       - application/json
 *     parameters:
 *       - name: bookId
 *         in: query
 *         required: true
 *         schema: 
 *           type: number
 *     responses:
 *       200:
 *         schema:
 *           type: ApiRestResponse
 *           $ref: '#/definitions/ApiRestResponse'
 */
router.get('/book-content', async (ctx, next) => {
  ctx.body = ApiRestResponse.success(bookContentService(ctx.query))
})
/**
 * @swagger
 * /book-content:
 *   post:
 *     tags: [book content controller]
 *     produces:
 *       - application/json
 *     parameters:
 *       - name: body
 *         in: body
 *         required: true
 *         schema: 
 *           type: BookContent
 *           $ref: '#/definitions/BookContent'
 *     responses:
 *       200:
 *         schema:
 *           type: ApiRestResponse
 *           $ref: '#/definitions/ApiRestResponse'
 */
router.post('/book-content', async (ctx, next) => {
  ctx.body = ApiRestResponse.success("增加")
})
/**
 * @swagger
 * /book-content:
 *   put:
 *     tags: [book content controller]
 *     produces:
 *       - application/json
 *     parameters:
 *       - name: body
 *         in: body
 *         required: true
 *         schema: 
 *           type: BookContent
 *           $ref: '#/definitions/BookContent'
 *     responses:
 *       200:
 *         schema:
 *           type: ApiRestResponse
 *           $ref: '#/definitions/ApiRestResponse'
 */
router.put('/book-content', async (ctx, next) => {
  ctx.body = ApiRestResponse.success("修改")
})
/**
 * @swagger
 * /book-content:
 *   delete:
 *     tags: [book content controller]
 *     produces:
 *       - application/json
 *     parameters:
 *       - name: body
 *         in: body
 *         required: true
 *         schema: 
 *           type: BookContent
 *           $ref: '#/definitions/BookContent'
 *     responses:
 *       200:
 *         schema:
 *           type: ApiRestResponse
 *           $ref: '#/definitions/ApiRestResponse'
 */
router.delete('/book-content', async (ctx, next) => {
  ctx.body = ApiRestResponse.success("删除")
})
module.exports = router
