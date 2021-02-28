const MultipleChoice = require('../model/multipleChoice');
const MultipleChoiceEntity = require('../entity/multipleChoice')

async function multipleChoiceById(id) {
    const _id = id
    const multipleChoice =await MultipleChoiceEntity.findOne({ _id })
    if(multipleChoice == null) {
        return null;
    } else {
        return new MultipleChoice(
            multipleChoice._id,
            multipleChoice.bookId,
            multipleChoice.issue,
            multipleChoice.translated,
            multipleChoice.resolve,
            multipleChoice.options
        )
    }
}
async function multipleChoiceByBookId(id) {
    const bookId = id
    const multipleChoice = await MultipleChoiceEntity.find({ bookId })
    if(multipleChoice == null) {
        return null;
    } else {
        const MultipleChoices = []
        multipleChoice.forEach(element => {
            MultipleChoices.push(new MultipleChoice(
                element._id,
                bookId,
                element.issue,
                element.translated,
                element.resolve,
                element.options
            ))
        });
        return MultipleChoices
    }
}
async function multipleChoiceInsertOne({
    bookId,
    issue,
    translated,
    resolve,
    options
}) {
    console.log(bookId);
    new MultipleChoiceEntity({
        bookId,
        issue,
        translated,
        resolve,
        options
    }).save()
}
async function multipleChoiceUpdate({
    id,
    bookId,
    issue,
    translated,
    resolve,
    options
}) {
    const _id = id;
    await MultipleChoiceEntity.updateOne({ _id }, {
        _id,
        bookId,
        issue,
        translated,
        resolve,
        options
    })
}
async function multipleChoiceDelete(id) {
    const _id = id;
    await MultipleChoiceEntity.deleteOne({
        _id
    })
}
module.exports = {
    multipleChoiceById,
    multipleChoiceByBookId,
    multipleChoiceInsertOne,
    multipleChoiceUpdate,
    multipleChoiceDelete
}