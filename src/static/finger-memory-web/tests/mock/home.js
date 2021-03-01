const json5 = require('json5');
const fs = require('fs');
const util = require('util');
const mock = require('mockjs');
const path = require('path');

const readFile = util.promisify(fs.readFile);
const mockDataDir = path.resolve(__dirname, '../', '../', 'public');

const memory = (app) => {
  const mockDataFile = path.resolve(mockDataDir, 'coreMemory.json5');
  app.get('/home/core-memory', (req, res) => {
    readFile(mockDataFile).then((data) => {
      res.json(
        mock.mock(json5.parse(data)),
      );
    }).catch((err) => {
      throw err;
    });
  });
};
const multipleChoice = (app) => {
  const mockDataFile = path.resolve(mockDataDir, 'coreMultipleChoice.json5');
  app.get('/home/core-multiple-choice', (req, res) => {
    readFile(mockDataFile).then((data) => {
      res.json(
        mock.mock(json5.parse(data)),
      );
    }).catch((err) => {
      throw err;
    });
  });
};
module.exports = {
  memory,
  multipleChoice,
};
