import Vue from 'vue';

export default {
  namespaced: true,
  state: {
    /* {
      '我的 卡组': [
        {
          id: 0,
          coveruUrl: 'https://placekitten.com/400/400?image=112',
          name: '测试',
          description: '我的测试卡组',
          author: '张三',
          max: 100,
          accomplish: 19,
        },
        {
          id: 1,
          coveruUrl: 'https://placekitten.com/400/400?image=113',
          name: '测试',
          description: '我的测试卡组',
          author: '张三',
          max: 100,
          accomplish: 22,
        },
        {
          id: 2,
          coveruUrl: 'https://placekitten.com/400/400?image=114',
          name: '测试',
          description: '我的测试卡组',
          author: '张三',
          max: 100,
          accomplish: 88,
        },
      ],
      '内置 卡组': [
        {
          id: 0,
          coveruUrl: 'https://placekitten.com/400/400?image=115',
          name: '测试',
          description: '我的测试卡组',
          author: 'admin',
          max: 100,
          accomplish: 55,
        },
        {
          id: 1,
          coveruUrl: 'https://placekitten.com/400/400?image=116',
          name: '内置测试',
          description: '我的测试卡组',
          author: 'admin',
          max: 100,
          accomplish: 77,
        },
        {
          id: 2,
          coveruUrl: 'https://placekitten.com/400/400?image=117',
          name: '内置测试',
          description: '我的测试卡组',
          author: '张三',
          max: 100,
          accomplish: 1,
        },
      ],
    } */
    list: {},
  },
  mutations: {
    initList(state, list) {
      state.list = list;
    },
  },
  actions: {
    getList(context) {
      Vue.axios.get('/me/me-list').then(({ data }) => {
        context.commit('initList', data);
      });
    },
  },
  modules: {
  },
};
