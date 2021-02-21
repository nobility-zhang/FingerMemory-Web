import Vue from 'vue';

export default {
  namespaced: true,
  state: {
    /* {
      '计 算 机 史': [
        {
          id: 0,
          coveruUrl: 'https://placekitten.com/400/600?image=2',
          name: '逻辑的引擎',
          description: `本书主要讲述了我们的现代计算机所基于的
          那些基本概念和发展出这些概念的人的生活与工作`,
          author: '（美）马丁·戴维斯著',
        },
        {
          id: 1,
          coveruUrl: 'https://placekitten.com/400/600?image=3',
          name: 'IT通史',
          description: `本书是一幅计算机产业发展的全景图。它以计算机产业六个发展时代为经线，
          以计算机技术发展中所有作出贡献的科学家和发明家的生平事迹、
          发明故事和在计算机企业竞争中涌现的著名企业家的经营管理、商战风云为纬线，
          纵横交织出生机勃勃和波澜壮阔的计算机产业的历史画卷。本书内容翔实、趣味性强、时间跨度大。
          阅读本书既可学到计算机技术的知识，更可学到计算机企业的经营管理知识和商战谋略`,
          author: '李彦',
        },
        {
          id: 2,
          coveruUrl: 'https://placekitten.com/400/600?image=4',
          name: '计算机技术发展史',
          description: `叙述20世纪60年代至80年代计算机技术的发展历史，重点论述晶体管计算机、
          中小规模集成电路计算机、微处理器与个人计算机以及计算机网络的诞生与发展，
          其中包括这一时期中国研究与开发计算机技术的成果。
          书中论述与分析了这个时期计算机技术发展的背景、技术基础与计算机的性能以及为这一时期计算机技术的
          发展做出贡献的人们的情况。`,
          author: '胡守仁',
        },
        {
          id: 3,
          coveruUrl: 'https://placekitten.com/400/600?image=5',
          name: '编码',
          description: `本书讲述的是计算机工作原理。
          作者用丰富的想象和清晰的笔墨将看似繁杂的理论阐述得通俗易懂，你
          丝毫不会感到枯燥和生硬。更重要的是，你会因此而获得对计算机工作原理较深刻的理解。
          这种理解不是抽象层面上的，而是具有一定深度的。`,
          author: 'Charles Petzold',
        },
        {
          id: 4,
          coveruUrl: 'https://placekitten.com/400/600?image=22',
          name: '编程人生',
          description: `其中包括对15位高水平程序员的采访。这些访谈中的主要主题包括受访者如何学习编程，
          他们如何调试代码，他们最喜欢的语言和工具，他们对读写编程，打样，代码阅读的看法等等`,
          author: '彼得·塞贝尔',
        },
        {
          id: 5,
          coveruUrl: 'https://placekitten.com/400/600?image=19',
          name: 'ACM图灵奖',
          description: `本书介绍了51位ACM图灵奖获得者的工作和事迹,
          通过对20世纪下半叶及21世纪初有代表性计算机科学家的介绍,
          多方位、多视角地反映计算机科学技术半个多世纪来的发展历程`,
          author: '崔林， 吴鹤龄',
        },
        {
          id: 6,
          coveruUrl: 'https://placekitten.com/400/600?image=7',
          name: 'C++语言的设计和演化',
          description: `本书论述了C++的历史和发展,C++中各种重要机制的本质意义和设计背景,
          以及这些机制的基本用途和使用方法,讨论了C++所适合的应用领域及其未来的发展前景。`,
          author: '比雅尼·斯特劳斯特鲁普',
        },
      ],
      '编 程 语 言': [
        {
          id: 10,
          coveruUrl: 'https://placekitten.com/400/600?image=125',
          name: '算法新解',
          description: `本书的一大特色就是提供了多种编程语言的算法实现代码，
          并且充分利用了各种语言特性。`,
          author: '刘新宇',
        },
        {
          id: 11,
          coveruUrl: 'https://placekitten.com/400/600?image=3',
          name: 'Python编程：从入门到实践',
          description: '帮助零基础读者迅速掌握Python编程',
          author: 'Eric Matthes',
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
      Vue.axios.get('/book/book-list').then(({ data }) => {
        context.commit('initList', data);
      });
    },
  },
  modules: {
  },
};
