<template>
  <b-row>
    <b-col md="6" v-for="i in 2" :key="i">
      <slot :name="i"></slot>
    </b-col>
  </b-row>
</template>
<script>
export default {
};
</script>
