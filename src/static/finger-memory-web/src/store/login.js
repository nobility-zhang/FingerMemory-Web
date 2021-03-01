import Vue from 'vue';
import router from '../router/index';

export default {
  namespaced: true,
  state: {
    userInfo: null,
  },
  mutations: {
    initUserInfo(state, userInfo) {
      state.userInfo = userInfo;
    },
    removeUserInfo(state) {
      state.userInfo = null;
    },
  },
  actions: {
    getUserInfo(context, payload) {
      Vue.axiosJava.post('/login', {
        ...payload,
      }).then(({ data }) => {
        context.commit('initUserInfo', data.data);
        router.replace('/me');
      });
    },
    removeUserInfo(context) {
      Vue.axiosJava.post('/logout').then(() => {
        context.commit('removeUserInfo');
        router.replace('/log-in');
      });
    },
  },
  modules: {},
};
