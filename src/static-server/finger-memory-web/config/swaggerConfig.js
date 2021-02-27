const router = require('koa-router')() //引入路由函数
const path = require('path')
const swaggerJSDoc = require('swagger-jsdoc')
const swaggerDefinition = {
    info: {
        title: 'FingerMemory-Web',
        version: '1.0.0',
        description: '使用Koa2开发，且仅用于静态数据的Api文档',
        contact: {
            name: "nobility-zhang",
            email: "nobility-zhang@foxmail.com",
            url: "http://nobility.work",
        }
    }
};
const config = {
    routePrefix: '/swagger-ui/index.html',
    swaggerOptions: {
        url: '/swagger.json',
    },
}
router.Config = config
const options = {
    swaggerDefinition,
    apis: [
        path.join(__dirname, '../routes/*.js'),
        path.join(__dirname, '../model/*.js'),
        path.join(__dirname, '../common/apiRestResponse.js'),
    ],
};
const swaggerSpec = swaggerJSDoc(options)

router.get('/swagger.json', async function (ctx) {
    ctx.set('Content-Type', 'application/json');
    ctx.body = swaggerSpec;
})
module.exports = router