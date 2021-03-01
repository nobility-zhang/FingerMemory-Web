import Vue from 'vue';

export default {
  namespaced: true,
  state: {
    /* [
      {
        id: 1,
        issue: '________ it is today!',
        translated: '________ 是今天!',
        resolve: `选A. 此题容易误选B. weather是不可数名词，前面不能有不定冠词。
        英语中经常考查的不可数名词有work, news, adivice, information等`,
        options: [
          { value: 'What fine weather', reight: true },
          { value: 'What a fine weather', reight: false },
          { value: 'How a fine weather', reight: false },
          { value: 'How fine a weather', reight: false },
        ],
      },
      {
        id: 2,
        issue: 'Which is the way to the __________?',
        translated: '哪条路是去 __________？',
        resolve: `选A. 此题极易误选B, C, D.
        英语中名词单数可以修饰名词，如：an apple tree—two apple trees.
        但注意 a man teacher—two men teachers。`,
        options: [
          { value: 'shoe factory', reight: true },
          { value: 'shoes factory', reight: false },
          { value: 'shoe’s factory', reight: false },
          { value: 'shoes’ factory', reight: false },
        ],
      },
      {
        id: 3,
        issue: 'This class ________ now. Miss Gao teaches them.',
        translated: '这门课现在是________。高老师教他们。',
        resolve: `选A. 此题容易误选B. class, family, team等单词如果表示整体谓语就用单数，
        表示个体就用复数，从语境及其后面的代词them可以看出，class表示个体，故选择A。`,
        options: [
          { value: 'are studying', reight: true },
          { value: 'is studying', reight: false },
          { value: 'be studying', reight: false },
          { value: 'studying', reight: false },
        ],
      },
      {
        id: 4,
        issue: 'We will have a _________ holiday after the exam',
        translated: '考试后我们将有一个_________假期',
        resolve: `选择B. 此题容易误选C, D. 在英语中数词中间用连字符号加名词单数，
        构成符合名词，在句中只能作定语， 如果选择C, 需要把逗号放s后面。`,
        options: [
          { value: 'two month', reight: false },
          { value: 'two-month', reight: true },
          { value: 'two month’s', reight: false },
          { value: 'two-months', reight: false },
        ],
      },
      {
        id: 5,
        issue: ' There is no enough ________ on the corner to put the table.',
        translated: '角落里没有足够的东西来放桌子________。',
        resolve: `选B. 此题最容易误选A.。room在句中是空间而非房间。
        句意：角落里没有摆桌子的空间了。`,
        options: [
          { value: 'place', reight: false },
          { value: 'room', reight: true },
          { value: 'floor', reight: false },
          { value: 'ground', reight: false },
        ],
      },
    ] */
    multipleChoices: [],
    /* [
      {
        id: 1,
        word: 'menu',
        translation: '菜单',
      },
      {
        id: 2,
        word: 'cursor',
        translation: '光标',
      },
      {
        id: 3,
        word: 'program',
        translation: '程序',
      },
      {
        id: 4,
        word: 'with',
        translation: '和...一起,用, 随着, 包括',
      },
      {
        id: 5,
        word: 'screen',
        translation: '屏幕',
      },
    ] */
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
    getMultipleChoices(context, payload) {
      Vue.axiosNode.get('/multiple-choice', {
        params: {
          ...payload,
        },
      }).then(({ data }) => {
        context.commit('initMultipleChoices', data.data);
      });
    },
    getMemorys(context) {
      Vue.axiosJava.get('/core-memory').then(({ data }) => {
        context.commit('initMemorys', data.data);
      });
    },
  },
  modules: {},
};
