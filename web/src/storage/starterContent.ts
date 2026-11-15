export const STARTER_LEXICON_ID = 'lexicon-starter';

export interface StarterWord {
  slug: string;
  term: string;
  meaning: string;
  hints: string[];
}

export const STARTER_WORDS: readonly StarterWord[] = [
  { slug: 'memory', term: 'memory', meaning: '记忆；内存', hints: ['与计算机存储有关'] },
  { slug: 'cursor', term: 'cursor', meaning: '光标', hints: ['屏幕上的位置标记'] },
  { slug: 'compiler', term: 'compiler', meaning: '编译器', hints: ['把源代码转换为目标代码'] },
  { slug: 'runtime', term: 'runtime', meaning: '运行时', hints: ['程序正在执行的阶段'] },
  { slug: 'buffer', term: 'buffer', meaning: '缓冲区', hints: ['临时存放数据'] },
  { slug: 'render', term: 'render', meaning: '渲染', hints: ['把数据转换为可见结果'] },
  { slug: 'promise', term: 'promise', meaning: '承诺；异步结果对象', hints: ['JavaScript 异步编程概念'] },
  { slug: 'socket', term: 'socket', meaning: '套接字', hints: ['网络通信端点'] },
  { slug: 'compile', term: 'compile', meaning: '编译', hints: ['构建过程中的一个动作'] },
  { slug: 'schema', term: 'schema', meaning: '模式；结构定义', hints: ['描述数据允许的形状'] },
] as const;
