/**
 *  @swagger
 *  definitions:
 *    ApiRestResponse:
 *      properties:
 *        status:
 *          type: number
 *        massage:
 *          type: string
 *        data:
 *          type: object
 */
const OK_CODE = 10000;
const OK_MASSAGE = "SUCCESS";
class ApiRestResponse {
    constructor(status, massage, data) {
        this.status = status;
        this.massage = massage;
        this.data = data;
    }
}
ApiRestResponse.success = function success(data, status = OK_CODE,massage = OK_MASSAGE) {
    return new ApiRestResponse(status, massage, data);
}
ApiRestResponse.error = function error({status, massage}) {
    return new ApiRestResponse(status, massage, null);
}
module.exports = ApiRestResponse

