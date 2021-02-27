module.exports = class GlobalError{
    constructor(status, massage){
        this.status = status;
        this.massage = massage;
    }
}