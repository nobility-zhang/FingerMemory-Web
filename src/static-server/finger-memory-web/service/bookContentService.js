const bookContentRepository = require('../repository/bookContentRepository');
const { Update_Error ,Repetitive_Operation_Error} = require('../error/error');

async function bookContentById(id) {
    return await bookContentRepository.bookContentById(id);
}

async function bookContentInsertOne(bookContent) {
    try {
        await bookContentRepository.bookContentInsertOne(bookContent);
        return null;
    } catch (error) {
        throw Repetitive_Operation_Error
    }
}

async function bookContentUpdate(bookContent) {
    if(await bookContentRepository.bookContentById(bookContent.id) == null) {
        throw Update_Error;
    }
    await bookContentRepository.bookContentUpdate(bookContent);
    return null;
}

async function bookContentDeleteOne({id}) {
    await bookContentRepository.bookContentDeleteOne(id);
    return null;
}

module.exports = {
    bookContentById,
    bookContentInsertOne,
    bookContentUpdate,
    bookContentDeleteOne
}