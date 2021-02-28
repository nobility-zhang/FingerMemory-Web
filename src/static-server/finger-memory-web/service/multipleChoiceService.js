const multipleChoiceRepository  = require('../repository/multipleChoiceRepository');
const { Update_Error } = require('../error/error');

async function multipleChoiceByBookId(bookId) {
    return multipleChoiceRepository.multipleChoiceByBookId(bookId);
}
async function multipleChoiceInsertOne (multipleChoice) {
    await multipleChoiceRepository.multipleChoiceInsertOne(multipleChoice);
    return null;
}
async function multipleChoiceUpdate(multipleChoice) {
    if(multipleChoice.id.length !== 24) {
        return null;
    }
    if(await multipleChoiceRepository.multipleChoiceById(multipleChoice.id) == null) {
        throw Update_Error;
    }
    await multipleChoiceRepository.multipleChoiceUpdate(multipleChoice);
    return null;
}
async function multipleChoiceDelete({id}) {
    if(id.length !== 24) {
        return null;
    } else {
        await multipleChoiceRepository.multipleChoiceDelete(id);
        return null;
    }
}

module.exports = {
    multipleChoiceByBookId,
    multipleChoiceInsertOne,
    multipleChoiceUpdate,
    multipleChoiceDelete
}