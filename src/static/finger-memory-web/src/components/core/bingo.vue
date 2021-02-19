<template>
  <b-toast
    :id="`toast-${bingoIdImpl}`"
    variant="success"
    solid
    toaster="b-toaster-top-center"
    no-auto-hide
    append-toast
  >
    <template #toast-title>
      <div class="d-flex flex-grow-1 align-items-baseline">恭喜你！答对了！</div>
    </template>
    <strong>答案解析：</strong>{{ resolveImpl }}
  </b-toast>
</template>
<script>
export default {
  props: ['resolve', 'bingoId'],
  data() {
    return {
      resolveImpl: this.resolve,
      bingoIdImpl: this.bingoId,
    };
  },
};
</script>
