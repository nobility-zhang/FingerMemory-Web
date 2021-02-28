const router = require('koa-router')()
const bookContentService = require("../service/bookContentService.js")
const ApiRestResponse = require("../common/apiRestResponse.js")
const { Not_Login } = require("../error/error")
const RedisHttpSession = require("../utils/redisHttpSession")
/**
 * @swagger
 * /book-content:
 *   get:
 *     tags: [book content controller]
 *     produces:
 *       - application/json
 *     parameters:
 *       - name: id
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
  ctx.body = ApiRestResponse.success(
   await bookContentService.bookContentById(ctx.query.id)
  )
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
  const islogin = await RedisHttpSession.isLogin(ctx);
  if (islogin) {
    try {
      ctx.body = ApiRestResponse.success(
        await bookContentService.bookContentInsertOne(ctx.request.body)
      )
    } catch (error) {
      ctx.body = ApiRestResponse.error(error)
    }
  } else {
    ctx.body = ApiRestResponse.error(Not_Login)
  }
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
  const islogin = await RedisHttpSession.isLogin(ctx);
  if (islogin) {
    try {
      await bookContentService.bookContentUpdate(ctx.request.body);
      ctx.body = ApiRestResponse.success(null)
    } catch (error) {
      ctx.body = ApiRestResponse.error(error)
    }
  } else {
    ctx.body = ApiRestResponse.error(Not_Login)
  }
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
  const islogin = await RedisHttpSession.isLogin(ctx);
  if (islogin) {
    ctx.body = ApiRestResponse.success(
      await bookContentService.bookContentDeleteOne(ctx.request.body)
    )
  } else {
    ctx.body = ApiRestResponse.error(Not_Login)
  }
})
module.exports = router