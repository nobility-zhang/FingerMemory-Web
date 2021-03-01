# 介绍

[FingerMemory-Web](https://github.com/nobility-zhang/FingerMemory-Web)是一套用于帮助Coder快速记忆单词的 Web App，与其他单词记忆App不同的是，该软件的设计是自底向上学习模式。单词记忆的核心是先使用肌肉记忆记住，随后慢慢理解，是喜欢使用键盘码字的人的福音。不仅易上手，还拥有插件机制，便于扩展，不同的Coder可以来进行定制化。

官网：http://nobility-zhang.github.io/FingerMemory-Web

项目dome：http://nobility.work/finger-memory（介于服务器的压力只简单部署了前端页面）

## 特色功能

> 未达成的目标之后会补上

- [x] 类似玩游戏的方式记忆单词
- [x] 对应英文书中的单词，学必见，见必学，提高英文文档阅读能力
- [x] 可自定义单词卡片
- [x] 可为每个单词添加笔记，使用自己的方式进行记忆，比如是某一段程序中的变量名
- [ ] 插件机制可让Coder为其定制化功能


## 浏览器兼容性

兼容常见的 PC 浏览器：Chrome，Firefox，Safari，Edge，QQ 浏览器，IE11。

目前不支持移动端。

## 编写此系统的初衷

大多数人都遇到过以下情况，在完全没有代码提示的情况下，会忘记某些单词的拼写，在查阅中往往会浪费很多时间，此时单词的拼写显得尤为重要，也是学英语的重要性

有很多记忆单词的软件，如anki、quizlet等，但是他都不怎么适合我，why？

- 有些人背诵时，用笔在纸上写
- 有些人背诵时，大声朗读出来
- 有些人背诵时，心里默默的读

我不属于这其中的任何一类人，我喜欢在背诵的时后使用键盘敲击，这样anki类的卡片记忆就无法满足，因为它们支支持那种卡片的记忆模式，对于coder来讲，手指记忆哪些没有任何逻辑的单词，比脑子记住要简单些
## 使用说明

> - Java后端模块请自行打包
> - Redis、MongoDB、MySQL请自行部署
>
> - nginx反向代理请自行配置

1. 下载项目

```bash
git clone https://github.com/nobility-zhang/FingerMemory-Web.git
cd FingerMemory-Web/src/
```

2. 配置跨域和数据库地址

```bash
#后端配置前端服务地址和数据库地址
vim dynamic-server/finger-memory-web/src/main/resources/application.yml
vim static-server/finger-memory-web/common/constant.js
#前端配置两个后端服务地址
vim static/finger-memory-web/src/plugins/axios-node.js
vim static/finger-memory-web/src/plugins/axios-java.js
```

3. 生成docker镜像

```bash
docker build -t static ./static
docker build -t static-server ./static-server
docker build -t dynamic-server ./dynamic-server
```

4. 启动docker容器（这里只是示例，不建议将其暴露给外网）

```bash
docker run -itd --name=static -p 5500:5500 static
docker run -itd --name=static-server -p 3000:3000 static-server
docker run -itd --name=dynamic-server -p 8080:8080 dynamic-server
```

## 后期计划

- [ ] 补充文档
- [ ] 优化代码
- [ ] 爬取数据、数据添加
- [ ] 增加我能想到的小功能