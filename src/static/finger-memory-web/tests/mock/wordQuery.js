const json5 = require("json5");
const fs = require("fs");
const util = require('util');
const mock = require("mockjs");
const path = require("path");

const readFile = util.promisify(fs.readFile);
const mockDataDir =  path.resolve(__dirname, "../", "../", "public");


const wordCard = (app) => {
  const mockDataFile =  path.resolve(mockDataDir, "wordCard.json5");
  app.get("/word/word-card", (req, res) => {
    readFile(mockDataFile).then((data) => {
      res.json(
        mock.mock(json5.parse(data))
      );
    }).catch((err) => {
      throw err;
    });
  })
}

const associates = (app) => {
  const mockDataFile =  path.resolve(mockDataDir, "associates.json5");
  app.get("/word/associates", (req, res) => {
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
  wordCard,
  associates,
};