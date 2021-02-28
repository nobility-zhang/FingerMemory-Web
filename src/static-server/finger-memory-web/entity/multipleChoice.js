const db = require('../config/mongoConfig')
module.exports = db.model(
    null,
    {
        bookId: Number,
        issue: String,
        translated: String,
        resolve: String,
        options: [
            { value: String },
            { reight: Boolean }
        ]
    },
    "multipleChoices"
)