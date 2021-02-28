const router = require('koa-router')()
const multipleChoiceService = require("../service/multipleChoiceService.js");
const ApiRestResponse = require("../common/apiRestResponse.js")
const { Not_Login, Update_Error} = require("../error/error")
const RedisHttpSession = require("../utils/redisHttpSession")
/**
 * @swagger
 * /multiple-choice:
 *   get:
 *     tags: [multiple choice controller]
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
router.get('/multiple-choice', async (ctx, next) => {
  ctx.body = ApiRestResponse.success(
    await multipleChoiceService.multipleChoiceByBookId(ctx.query.bookId)
  )
})
/**
 * @swagger
 * /multiple-choice:
 *   post:
 *     tags: [multiple choice controller]
 *     produces:
 *       - application/json
 *     parameters:
 *       - name: body
 *         in: body
 *         required: true
 *         schema: 
 *           type: MultipleChoice
 *           $ref: '#/definitions/MultipleChoice'
 *     responses:
 *       200:
 *         schema:
 *           type: ApiRestResponse
 *           $ref: '#/definitions/ApiRestResponse'
 */
router.post('/multiple-choice', async (ctx, next) => {
  const isLogin = await RedisHttpSession.isLogin(ctx);
  if(isLogin) {
    ctx.body = ApiRestResponse.success(
      await multipleChoiceService.multipleChoiceInsertOne(ctx.request.body)
    )
  } else {
    ctx.body = ApiRestResponse.error(Not_Login)
  }
})
/**
 * @swagger
 * /multiple-choice:
 *   put:
 *     tags: [multiple choice controller]
 *     produces:
 *       - application/json
 *     parameters:
 *       - name: body
 *         in: body
 *         required: true
 *         schema: 
 *           type: MultipleChoice
 *           $ref: '#/definitions/MultipleChoice'
 *     responses:
 *       200:
 *         schema:
 *           type: ApiRestResponse
 *           $ref: '#/definitions/ApiRestResponse'
 */
router.put('/multiple-choice', async (ctx, next) => {
  const isLogin = await RedisHttpSession.isLogin(ctx);
  if(isLogin) {
    try {
      await multipleChoiceService.multipleChoiceUpdate(ctx.request.body);
      ctx.body = ApiRestResponse.success(null)
    } catch (error) {
      ctx.body = ApiRestResponse.error(Update_Error)
    }
  } else {
    ctx.body = ApiRestResponse.error(Not_Login)
  }
})
/**
 * @swagger
 * /multiple-choice:
 *   delete:
 *     tags: [multiple choice controller]
 *     produces:
 *       - application/json
 *     parameters:
 *       - name: body
 *         in: body
 *         required: true
 *         schema: 
 *           type: MultipleChoice
 *           $ref: '#/definitions/MultipleChoice'
 *     responses:
 *       200:
 *         schema:
 *           type: ApiRestResponse
 *           $ref: '#/definitions/ApiRestResponse'
 */
router.delete('/multiple-choice', async (ctx, next) => {
  const isLogin = await RedisHttpSession.isLogin(ctx);
  if(isLogin) {
    console.log(ctx.request.body);
    ctx.body = ApiRestResponse.success(
      await multipleChoiceService.multipleChoiceDelete(ctx.request.body)
    )
  } else {
    ctx.body = ApiRestResponse.error(Not_Login)
  }
})

module.exports = router
