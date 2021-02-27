const client = require('../config/redisConfig')
const {
    SESSION_KEY
} = require('../common/constant')
client.promiseGet = function (key) {
    return new Promise((resolve, reject) => {
        client.get(key, (err, data) => {
            if (err) {
                reject(err);
            } else {
                resolve(data);
            }
        })
    })
}

client.promiseSet = function (key, value) {
    return new Promise((resolve, reject) => {
        client.set(key, value, (err) => {
            if (err) {
                reject(err);
            } else {
                resolve();
            }
        })
    })
}

client.promiseHget = function (key, name) {
    return new Promise((resolve, reject) => {
        client.hget(key, name, (err, data) => {
            if (err) {
                reject(err);
            } else {
                resolve(data);
            }
        })
    })
}

client.promiseHset = function (key, name, value) {
    return new Promise((resolve, reject) => {
        client.hset(key, name, value, (err) => {
            if (err) {
                reject(err);
            } else {
                resolve();
            }
        })
    })
}

client.promiseExists = function (key) {
    return new Promise((resolve, reject) => {
        client.exists(key, (err, data) => {
            if (err) {
                reject(err);
            } else {
                resolve(data)
            }
        })
    })
}

async function getAttribute(sessionId, name) {
    return await client.promiseHget(sessionId, name);
}

async function setAttribute(sessionId, name, value) {
    await client.promiseHSet(sessionId, name, value);
}

async function removeAttribute(sessionId, name) {
    await client.promiseHdel(sessionId, name)
}

function getSessionId(ctx) {
    return ctx.cookies.get(SESSION_KEY);
}

async function isLogin(ctx) {
    const sessionId = getSessionId(ctx)
    if (sessionId == null) {
        return false;
    } else {
        const isExists = await client.promiseExists(sessionId);
        if (isExists) {
            return true;
        } else {
            return false;
        }
    }
}

module.exports = {
    getAttribute,
    setAttribute,
    removeAttribute,
    getSessionId,
    isLogin,
}