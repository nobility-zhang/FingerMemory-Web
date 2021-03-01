const json5 = require('json5');
const fs = require('fs');
const util = require('util');
const mock = require('mockjs');
const path = require('path');

const readFile = util.promisify(fs.readFile);
const mockDataDir = path.resolve(__dirname, '../', '../', 'public');

const bookList = (app) => {
  const mockDataFile = path.resolve(mockDataDir, 'bookList.json5');
  app.get('/book/book-list', (req, res) => {
    readFile(mockDataFile)
      .then((data) => {
        res.json(
          mock.mock(json5.parse(data)),
        );
      })
      .catch((err) => {
        throw err;
      });
  });
};
const searchList = (app) => {
  const mockDataFile = path.resolve(mockDataDir, 'searchList.json5');
  app.get('/book/search-list', (req, res) => {
    readFile(mockDataFile)
      .then((data) => {
        res.json(
          mock.mock(json5.parse(data)),
        );
      })
      .catch((err) => {
        throw err;
      });
  });
};
module.exports = {
  bookList,
  searchList,
};
