<template>
  <b-row>
    <b-tabs content-class="mt-3" class="w-100">
      <b-tab
        v-for="(value, key, index) in list"
        :key="index"
        :title="key"
        :active="index == 0"
      >
        <list-card-framework
        :cardList="value"
        :type="type">
        </list-card-framework>
      </b-tab>
      <slot></slot>
    </b-tabs>
  </b-row>
</template>
<script>
import listCardFramework from '@/components/tapList/listCardFramework.vue';

export default {
  props: ['type', 'list'],
  components: {
    listCardFramework,
  },
};
</script>
