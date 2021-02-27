const router = require('koa-router')()
const upload = require('../config/uploadConfig')
const { uploadService } = require("../service/uploadService")
const ApiRestResponse = require("../common/apiRestResponse")
 /**
   * @swagger
   * /upload:
   *   post:
   *     tags: [upload controller]
   *     produces:
   *       - application/json
   *     consumes: multipart/form-data
   *     parameters:
   *       - name: file
   *         in: formData
   *         required: true
   *         type: file
   *     responses:
   *       200:
   *         type: ApiRestResponse
   *         $ref: '#/definitions/ApiRestResponse'
   */
router.post('/upload', upload.single("file"), async (ctx, next) => {
  ctx.body = ApiRestResponse.success(uploadService(ctx));
})

module.exports = router
