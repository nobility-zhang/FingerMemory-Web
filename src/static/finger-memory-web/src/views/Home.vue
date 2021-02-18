<template>
  <div>
    <b-jumbotron bg-variant="light" text-variant="dark" border-variant="dark">
      <template v-slot:header>FingerMemory-Web</template>
      <template v-slot:lead>
        <ul>
          <li>类似玩游戏的方式记忆单词</li>
          <li>对应英文书中的单词，学必见，见必学，提高英文文档阅读能力</li>
          <li>可自定义单词卡片</li>
          <li>
            可为每个单词添加笔记，使用自己的方式进行记忆，比如是某一段程序中的变量名
          </li>
          <li>插件机制可让Coder为其定制化功能</li>
        </ul>
      </template>
      <hr/>
      <b-alert show variant="success">
        随时随地的享受单词记忆的快乐，一切尽在 FingerMemory
      </b-alert>
      <div class="d-flex justify-content-center">
        <b-button variant="primary" href="#">快速开始</b-button>
      </div>
    </b-jumbotron>
  </div>
</template>
<script>
// import tapList from '@/components/tapList.vue';

export default {
  components: {
    // tapList,
  },
};
</script>
