const bookList = require('./tests/mock/bookList.js');
const me = require('./tests/mock/me.js');
const home = require('./tests/mock/home.js');
const wordQuery = require('./tests/mock/wordQuery.js');

const forEachCall = (app) => {
  const mockFunctionObj = {
    ...bookList,
    ...me,
    ...home,
    ...wordQuery,
  };
  for (const key in mockFunctionObj) {
    if (Object.hasOwnProperty.call(mockFunctionObj, key)) {
      const element = mockFunctionObj[key];
      element(app);
    }
  }
};

module.exports = {
  devServer: {
    port: 5500,
    before(app) {
      forEachCall(app);
    },
  },
};
