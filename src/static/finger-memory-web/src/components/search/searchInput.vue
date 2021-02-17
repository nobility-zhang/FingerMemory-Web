<template>
  <b-form-group :label="titleImpl">
    <b-form-input
      :placeholder="placeholderImpl"
    ></b-form-input>
  </b-form-group>
</template>
<script>
export default {
  props: ['title', 'placeholder'],
  data() {
    return {
      titleImpl: this.title,
      placeholderImpl: this.placeholder,
    };
  },
};
</script>
