const GlobalError = require('./globalError');
module.exports = {
    Not_Login: new GlobalError(10002, "未登入，请先登入"),
    Delete_Error: new GlobalError(10003, "删除错误"),
    Update_Error: new GlobalError(10004, "更新错误"),
    Repetitive_Operation_Error: new GlobalError(10007, "重复操作错误"),
    
    System_Error: new GlobalError(20000, "系统错误"),
}