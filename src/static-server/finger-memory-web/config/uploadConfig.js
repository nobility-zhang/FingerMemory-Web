const multer = require('koa-multer')
const path = require('path')

const uploadDir = path.join('./public')
const size = 5;

const storage = multer.diskStorage({
    limits: {
        fileSize: size * 1024
    },
    destination(req, file, cb) {
        cb(null, uploadDir);
    },
    filename(req, file, cb) {
        cb(null, Date.now() + '-' + file.originalname);
    }
})
const upload = multer({
    storage
})
module.exports = upload;