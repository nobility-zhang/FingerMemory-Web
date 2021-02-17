<template>
  <div>
    <div class="mb-2">{{titleImpl}}</div>
    <b-form-select v-model="selected" :options="options"></b-form-select>
  </div>
</template>
<script>
export default {
  props: ['title', 'options'],
  data() {
    return {
      titleImpl: this.title,
      optionsImpl: this.options,
      selected: this.options[0],
    };
  },
};
</script>
