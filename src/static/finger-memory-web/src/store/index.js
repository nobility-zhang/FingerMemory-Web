import Vue from 'vue';
import Vuex from 'vuex';
import bookList from './bookList';
import me from './me';
import wordQuery from './wordQuery';
import home from './home';

Vue.use(Vuex);

export default new Vuex.Store({
  state: {
  },
  mutations: {
  },
  actions: {
  },
  modules: {
    bookList,
    me,
    wordQuery,
    home,
  },
});
