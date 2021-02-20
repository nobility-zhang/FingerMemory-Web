import Vue from 'vue';

export default {
  namespaced: true,
  state: {
    list: [],
  },
  mutations: {
    initList(state, list) {
      state.list = list;
    },
  },
  actions: {
    getList(context) {
      Vue.axios.get('/book/book-list').then(({ data }) => {
        context.commit('initList', data);
      });
    },
  },
  modules: {
  },
};
