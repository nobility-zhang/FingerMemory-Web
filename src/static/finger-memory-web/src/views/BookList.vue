<template>
  <b-container class="bv-example-row">
    <search
    title="单词图书馆"
    placeholder="请输入要查询的图书"
    random-search="随机推荐"
    type="book"
    ></search>
    <tap-list
    type="book"
    :list="list"
    >
    </tap-list>
  </b-container>
</template>
<script>
import search from '@/components/search.vue';
import tapList from '@/components/tapList.vue';
import { mapState } from 'vuex';

export default {
  computed: {
    ...mapState({
      list: (state) => state.bookList.list,
    }),
  },
  created() {
    this.$store.dispatch('bookList/getList');
  },
  components: { search, tapList },
};
</script>
