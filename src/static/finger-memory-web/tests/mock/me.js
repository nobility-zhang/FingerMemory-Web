const json5 = require("json5");
const fs = require("fs");
const util = require('util');
const mock = require("mockjs");
const path = require("path");

const readFile = util.promisify(fs.readFile);
const mockDataDir =  path.resolve(__dirname, "../", "../", "public");

const meList = (app) => {
  const mockDataFile =  path.resolve(mockDataDir, "meList.json5");
  app.get("/me/me-list", (req, res) => {
    readFile(mockDataFile).then((data) => {
      res.json(
        mock.mock(json5.parse(data))
      );
    }).catch((err) => {
      throw err;
    });
  })
}
module.exports = {
  meList
};