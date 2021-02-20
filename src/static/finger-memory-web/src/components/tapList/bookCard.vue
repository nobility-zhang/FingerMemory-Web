<template>
  <b-card no-body class="overflow-hidden">
    <b-row no-gutters>
      <b-col md="6">
        <b-card-img :src="cardImpl.coveruUrl" class="rounded-0"></b-card-img>
      </b-col>
      <b-col md="6">
        <b-card-body :title="cardImpl.name">
          <b-link href="#" class="card-link">{{cardImpl.author}}</b-link>
          <b-card-text class="mt-3 multiline-ellipsis text-indent">
            {{cardImpl.description}}
          </b-card-text>
        </b-card-body>
      </b-col>
    </b-row>
  </b-card>
</template>
<script>
export default {
  props: ['card'],
  data() {
    return {
      cardImpl: this.card,
    };
  },
};
</script>
<style lang="scss" scoped>
.text-indent {
  text-indent: 2rem;
}
.multiline-ellipsis {
    overflow: hidden;    /* 内容溢出隐藏 */
    text-overflow: ellipsis;    /* 文字溢出省略号 */
    white-space: normal;    /* 文字换行 */
    display: -webkit-box;
    -webkit-line-clamp: 5;  /* 行数 */
    -webkit-box-orient: vertical;
}
</style>
