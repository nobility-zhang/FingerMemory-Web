<template>
<div>
  <div class="btn-group d-flex justify-content-center" role="group">
    <button type="button" class="btn btn-secondary" @click="memory">记忆模式</button>
    <button type="button" class="btn btn-secondary" @click="multipleChoice">做题模式</button>
  </div>
  <component :is="type"></component>
</div>

</template>
<script>
import coreMemoryGroup from '@/components/core/coreMemoryGroup.vue';
import coreMultipleChoiceGroup from '@/components/core/coreMultipleChoiceGroup.vue';

export default {
  data() {
    return {
      type: 'coreMemoryGroup',
    };
  },
  methods: {
    memory() {
      this.type = 'coreMemoryGroup';
    },
    multipleChoice() {
      this.type = 'coreMultipleChoiceGroup';
    },
  },
  components: {
    coreMemoryGroup,
    coreMultipleChoiceGroup,
  },
};
</script>
