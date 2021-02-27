function uploadService(ctx) {
    return ctx.request.origin + "/" + ctx.req.file.filename
}

module.exports = {
    uploadService,
}