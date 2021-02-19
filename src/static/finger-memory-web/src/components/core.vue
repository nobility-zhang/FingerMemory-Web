<template>
  <component :is="componentType(typeImpl)"></component>
</template>
<script>
import coreMemoryGroup from '@/components/core/coreMemoryGroup.vue';
import coreMultipleChoiceGroup from '@/components/core/coreMultipleChoiceGroup.vue';

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
      return `core${newType}Group`;
    },
  },
  components: {
    coreMemoryGroup,
    coreMultipleChoiceGroup,
  },
};
</script>
