const mongoose = require('mongoose')
const {MONGO_HOST, MONGO_PROT} = require('../common/constant')

const db = mongoose.createConnection(`mongodb://${MONGO_HOST}:${MONGO_PROT}`, {
    dbName: "fm",
    poolSize: 5,
    connectTimeoutMS: 3000,
    socketTimeoutMS: 20000,
    keepAlive: true,
    autoIndex: true,
    useNewUrlParser: true,
    useUnifiedTopology: true,
}, err => {
    if (err) throw err
})
module.exports = db;