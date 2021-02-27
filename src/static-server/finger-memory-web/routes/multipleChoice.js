const router = require('koa-router')()
const { multipleChoiceService } = require("../service/multipleChoiceService.js");
const ApiRestResponse = require("../common/apiRestResponse.js")
/**
 * @swagger
 * /multiple-choice:
 *   get:
 *     tags: [multiple choice controller]
 *     produces:
 *       - application/json
 *     parameters:
 *       - name: multipleChoiceId
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
  ctx.body = ApiRestResponse.success(multipleChoiceService(ctx.query))
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
 *           type: MultipleChoiceService
 *           $ref: '#/definitions/MultipleChoice'
 *     responses:
 *       200:
 *         schema:
 *           type: ApiRestResponse
 *           $ref: '#/definitions/ApiRestResponse'
 */
router.post('/multiple-choice', async (ctx, next) => {
  ctx.body = ApiRestResponse.success("增加")
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
 *           type: MultipleChoiceService
 *           $ref: '#/definitions/MultipleChoice'
 *     responses:
 *       200:
 *         schema:
 *           type: ApiRestResponse
 *           $ref: '#/definitions/ApiRestResponse'
 */
router.put('/multiple-choice', async (ctx, next) => {
  ctx.body = ApiRestResponse.success("更新")
})
/**
 * @swagger
 * /multiple-choice:
 *   delete:
 *     tags: [multiple choice controller]
 *     produces:
 *       - application/json
 *     parameters:
 *       - name: multipleChoiceId
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
router.delete('/multiple-choice', async (ctx, next) => {
  ctx.body = ApiRestResponse.success("删除")
})

module.exports = router
