<template>
  <component :is="componentType(typeImpl)"></component>
</template>
<script>
import searchGroupWord from '@/components/search/searchGroupWord.vue';
import searchGroupBook from '@/components/search/searchGroupBook.vue';

export default {
  props: ['type'],
  data() {
    return {
      typeImpl: this.type,
    };
  },
  methods: {
    componentType(type) {
      const newType = type.slice(0, 1).toUpperCase() + type.slice(1);
      return `searchGroup${newType}`;
    },
  },
  components: {
    searchGroupWord,
    searchGroupBook,
  },
};
</script>
