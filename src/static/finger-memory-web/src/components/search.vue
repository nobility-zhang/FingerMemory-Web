<template>
  <div>
    <search-header :title="titleImpl" :placeholder="placeholderImpl">
    </search-header>
    <b-row>
      <b-col md="2" offset-md="3">
        <b-link v-b-toggle.collapse>高级搜索</b-link>
      </b-col>
      <b-col md="2" offset-md="3">
        <b-link href="#">{{randomSearchImpl}}</b-link>
      </b-col>
    </b-row>
    <b-collapse id="collapse">
      <searchGroup :type="typeImpl">
      </searchGroup>
    </b-collapse>
  </div>
</template>
<script>
import searchGroup from '@/components/search/searchGroup.vue';
import searchHeader from '@/components/search/searchHeader.vue';

export default {
  props: ['title', 'placeholder', 'randomSearch', 'type'],
  data() {
    return {
      titleImpl: this.title,
      placeholderImpl: this.placeholder,
      randomSearchImpl: this.randomSearch,
      typeImpl: this.type,
    };
  },
  components: {
    searchGroup,
    searchHeader,
  },
};
</script>
