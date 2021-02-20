import Vue from 'vue';

export default {
  namespaced: true,
  state: {
    multipleChoices: [],
    memorys: [],
  },
  mutations: {
    initMultipleChoices(state, multipleChoices) {
      state.multipleChoices = multipleChoices;
    },
    initMemorys(state, memorys) {
      state.memorys = memorys;
    },
  },
  actions: {
    getMultipleChoices(context) {
      Vue.axios.get('/home/core-multiple-choice').then(({ data }) => {
        context.commit('initMultipleChoices', data);
      });
    },
    getMemorys(context) {
      Vue.axios.get('/home/core-memory').then(({ data }) => {
        context.commit('initMemorys', data);
      });
    },
  },
  modules: {
  },
};
