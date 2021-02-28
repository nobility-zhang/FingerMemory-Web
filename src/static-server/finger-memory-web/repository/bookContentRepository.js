const BookContent = require('../model/bookContent');
const BookContentEntiry = require('../entity/bookContent')
async function bookContentById(id) {
    const bookContent = await BookContentEntiry.findOne({ id });
    if (bookContent == null) {
        return null;
    } else {
        return new BookContent(
            bookContent.id,
            bookContent.englishTitle,
            bookContent.translateTitle,
            bookContent.englishContent,
            bookContent.translateContent,
            bookContent.mark,
            bookContent.note
        );
    }
}

async function bookContentInsertOne({
    id,
    englishTitle,
    translateTitle,
    englishContent,
    translateContent,
    mark,
    note
}) {
    await new BookContentEntiry({
        id,
        englishTitle,
        translateTitle,
        englishContent,
        translateContent,
        mark,
        note
    }).save();
}

async function bookContentUpdate({
    id,
    englishTitle,
    translateTitle,
    englishContent,
    translateContent,
    mark,
    note
}) {
    await BookContentEntiry.updateOne(
        { id },
        { $set: { 
            id,
            englishTitle,
            translateTitle,
            englishContent,
            translateContent,
            mark,
            note
        } },
    )
}

async function bookContentDeleteOne(id) {
    await BookContentEntiry.deleteOne({
        id
    })
}

module.exports = {
    bookContentById,
    bookContentInsertOne,
    bookContentUpdate,
    bookContentDeleteOne
}