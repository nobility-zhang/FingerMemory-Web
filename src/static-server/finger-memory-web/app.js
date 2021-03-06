const Koa = require('koa')
const app = new Koa()
const json = require('koa-json')
const onerror = require('koa-onerror')
const bodyparser = require('koa-bodyparser')
const logger = require('koa-logger')
const { koaSwagger } = require('koa2-swagger-ui')

const {ORIGIN_URL} = require('./common/constant')
const swagger = require('./config/swaggerConfig')
const bookContent = require('./routes/bookContent')
const multipleChoice = require('./routes/multipleChoice')
const upload = require('./routes/upload')
const cors = require('koa2-cors');

// error handler
onerror(app)

// middlewares
app.use(bodyparser({
  enableTypes:['json', 'form', 'text'],
  jsonLimit: '10mb',
  formLimit: '10mb',
  textLimit: '10mb'
}))
app.use(json())
app.use(logger())
app.use(require('koa-static')(__dirname + '/public'))
app.use(koaSwagger(swagger.Config))
app.use(cors({
  origin: function (ctx) {
      return ORIGIN_URL;
  },
  credentials: true,
  allowMethods: ['GET', 'POST', 'DELETE', 'PUT'],
}))

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
