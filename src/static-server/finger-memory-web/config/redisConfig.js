const redis = require('redis')
const {
    REDIS_HOST,
    REDIS_PROT
} = require('../common/constant')
const client = redis.createClient(REDIS_PROT, REDIS_HOST)

client.on('error', function (err) {
    throw err;
});

client.once('connect', () => {
    console.log("connect success");
})

module.exports = client;