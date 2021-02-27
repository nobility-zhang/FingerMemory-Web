const Koa = require('koa')
const app = new Koa()
const json = require('koa-json')
const onerror = require('koa-onerror')
const bodyparser = require('koa-bodyparser')
const logger = require('koa-logger')
const { koaSwagger } = require('koa2-swagger-ui')

const swagger = require('./config/swaggerConfig')
const bookContent = require('./routes/bookContent')
const multipleChoice = require('./routes/multipleChoice')
const upload = require('./routes/upload')

// error handler
onerror(app)

// middlewares
app.use(bodyparser({
  enableTypes:['json', 'form', 'text']
}))
app.use(json())
app.use(logger())
app.use(require('koa-static')(__dirname + '/public'))
app.use(koaSwagger(swagger.Config))

// logger
app.use(async (ctx, next) => {
  const start = new Date()
  await next()
  const ms = new Date() - start
  console.log(`${ctx.method} ${ctx.url} - ${ms}ms`)
})

// routes
app.use(swagger.routes(), swagger.allowedMethods())
app.use(bookContent.routes(), bookContent.allowedMethods())
app.use(multipleChoice.routes(), multipleChoice.allowedMethods())
app.use(upload.routes(), bookContent.allowedMethods())

// error-handling
app.on('error', (err, ctx) => {
  console.error('server error', err, ctx)
});

module.exports = app
