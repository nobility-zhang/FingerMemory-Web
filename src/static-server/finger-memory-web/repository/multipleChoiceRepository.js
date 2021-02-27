const MultipleChoice = require('../model/multipleChoice');
const Option = require('../model/option');

function multipleChoiceById(id) {
    return new MultipleChoice(
        1,
        "问题",
        "译文",
        "解析",
        [
            new Option("答案1", true),
            new Option("答案1", false),
            new Option("答案1", false),
            new Option("答案1", false),
        ]
        );
}
module.exports = {
    multipleChoiceById,
}