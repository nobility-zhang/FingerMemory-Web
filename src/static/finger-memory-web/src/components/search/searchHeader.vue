<template>
  <div>
    <h1 class="text-center">{{titleImpl}}</h1>
    <b-row>
      <b-col md="5" offset-md="3" class="mr-0 pr-0">
        <b-form-input :placeholder="placeholderImpl" v-model="text"></b-form-input>
      </b-col>
      <b-col md="2">
        <b-button type="submit" @click="search">搜索</b-button>
      </b-col>
    </b-row>
  </div>
</template>
<script>
export default {
  props: ['title', 'placeholder'],
  data() {
    return {
      text: '',
      titleImpl: this.title,
      placeholderImpl: this.placeholder,
    };
  },
  methods: {
    search() {
      this.$store.dispatch('wordQuery/getAssociates', {
        contain: this.text[0],
      });
      this.$store.dispatch('wordQuery/getCard', {
        word: this.text,
      });
    },
  },
};
</script>
