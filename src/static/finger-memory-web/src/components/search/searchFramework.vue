<template>
  <div>
    <b-row v-for="i in 2" :key="i">
      <b-col v-for="j in 3" :key="j">
        <slot :name="i+'-'+j"></slot>
      </b-col>
    </b-row>
  </div>
</template>
<script>
export default {
};
</script>
