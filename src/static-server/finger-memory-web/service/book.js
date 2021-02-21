const { bookById } = require('../repository/book');

function bookService(query) {
    return bookById(query.id);
}

module.exports = {
    bookService,
}