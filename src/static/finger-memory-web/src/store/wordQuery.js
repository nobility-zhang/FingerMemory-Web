import Vue from 'vue';

export default {
  namespaced: true,
  state: {
    associates: [
      {
        id: 1,
        url: '#',
        english: 'english',
        translation: '英文',
        description: `英格兰人的；英格兰的；
        英文的；英语；英格兰人；（台球中的）侧旋；
        把……译成英语；（美、加、英、澳）英格利希（人名）`,
      },
      {
        id: 2,
        url: '#',
        english: 'english',
        translation: '英文',
        description: `英格兰人的；英格兰的；
        英文的；英语；英格兰人；（台球中的）侧旋；
        把……译成英语；（美、加、英、澳）英格利希（人名）`,
      },
      {
        id: 3,
        url: '#',
        english: 'english',
        translation: '英文',
        description: `英格兰人的；英格兰的；
        英文的；英语；英格兰人；（台球中的）侧旋；
        把……译成英语；（美、加、英、澳）英格利希（人名）`,
      },
    ],
    card: {
    },
  },
  mutations: {
    initAssociates(state, associates) {
      state.associates = associates;
    },
    initCard(state, card) {
      state.card = card;
    },
  },
  actions: {
    getAssociates(context) {
      Vue.axios.get('/word/associates').then(({ data }) => {
        context.commit('initAssociates', data);
        console.log(data);
      });
    },
    getCard(context) {
      Vue.axios.get('/word/word-card').then(({ data }) => {
        context.commit('initCard', data);
      });
    },
  },
  modules: {
  },
};
