const { bookContentById } = require('../repository/bookContentRepository');

function bookContentService(query) {
    return bookContentById(query.id);
}

module.exports = {
    bookContentService,
}