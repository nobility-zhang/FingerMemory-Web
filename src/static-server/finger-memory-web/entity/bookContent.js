const db = require('../config/mongoConfig')

module.exports = db.model(
    null,
    {
        id: {type: Number, index: { unique: true }},
        englishTitle: String,
        translateTitle: String,
        englishContent: String,
        translateContent: String,
        mark: String,
        note: String
    },
    "bookContents"
)