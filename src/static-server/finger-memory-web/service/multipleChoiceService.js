const { multipleChoiceById } = require('../repository/multipleChoiceRepository');

function multipleChoiceService(query) {
    return multipleChoiceById(query.id);
}

module.exports = {
    multipleChoiceService,
}