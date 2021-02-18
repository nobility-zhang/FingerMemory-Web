<template>
  <b-container class="bv-example-row">
    <search
    title="查找一个单词"
    placeholder="请输入要查询的单词"
    random-search="每日一词"
    type="word"
    ></search>
    <word></word>
  </b-container>
</template>
<script>
import search from '@/components/search.vue';
import word from '@/components/word.vue';

export default {
  components: {
    search,
    word,
  },
};
</script>
