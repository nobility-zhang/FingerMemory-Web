const router = require('koa-router')()
const { bookService } = require("../service/book.js");

router.get('/', async (ctx, next) => {
  ctx.body = {
    title: 'hello world'
  }
})

router.get('/book', async (ctx, next) => {
  ctx.body = bookService(ctx.query);
})

module.exports = router
