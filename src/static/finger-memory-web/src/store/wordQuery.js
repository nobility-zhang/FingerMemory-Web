import Vue from 'vue';

export default {
  namespaced: true,
  state: {
    /* [
      {
        id: 1,
        url: '#',
        english: 'memory',
        translation: '记忆,内存',
        description: `内存是计算机的重要部件之一。
        它是外存与CPU进行沟通的桥梁，计算机中所有程序的运行都在内存中进行。
        内存性能的强弱影响计算机整体发挥的水平。`,
      },
      {
        id: 2,
        url: '#',
        english: 'finger',
        translation: '手指',
        description: `手指是指人手前端的五个分支。手掌的五个终端部分之一，
        手指一般有拇指、食指、中指、无名指、小指这五个。话说：十指连心。
        人类的手指蕴涵着人身的许多秘密。手指是我们人体器官的重要组成部分，
        也为我们的生活带来很多的帮助`,
      },
      {
        id: 3,
        url: '#',
        english: 'encoding',
        translation: '编码',
        description: `编码是信息从一种形式或格式转换为另一种形式的过程，
        也称为计算机编程语言的代码简称编码。用预先规定的方法将文字、数字或其它对象编成数码，
        或将信息、数据转换成规定的电脉冲信号。编码在电子计算机、电视、遥控和通讯等方面广泛使用。
        编码是信息从一种形式或格式转换为另一种形式的过程。`,
      },
    ] */
    associates: [],
    /* {
      id: 0,
      imageUrl: 'https://placekitten.com/380/200',
      english: 'cat',
      translation: '猫',
      description: `猫，属于猫科动物，分家猫、野猫，是全世界家庭中较为广泛的宠物。
      家猫的祖先据推测是古埃及的沙漠猫，波斯的波斯猫，已经被人类驯化了3500年
      （但未像狗一样完全地被驯化）`,
      note: `Tomcat是Apache 软件基金会（Apache Software Foundation）的Jakarta项目中的一个核心项目，
      由Apache、Sun 和其他一些公司及个人共同开发而成。由于有了Sun 的参与和支持，最新的Servlet 和JSP
      规范总是能在Tomcat 中得到体现，Tomcat 5支持最新的Servlet 2.4 和JSP 2.0 规范。因为Tomcat 技术先进、性能稳定，而且免费，
      因而深受Java 爱好者的喜爱并得到了部分软件开发商的认可，成为目前比较流行的Web 应用服务器。`,
    } */
    card: {},
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
