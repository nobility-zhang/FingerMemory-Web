<template>
  <div class="card border border-info">
    <div class="card-header text-center bg-info text-white">记忆卡片</div>
    <div class="card-body text-center text-dark">
      <h2 class="card-title">{{ memory.english }}</h2>
      <b-link href="#" @click="play">
        <audio
          :src="'http://dict.youdao.com/dictvoice?type=0&audio=' + memory.english"
          ref="audio"
        >
        </audio>
        <strong>{{ memory.translation }}</strong>
      </b-link>
      <b-form-input
      @keyup.enter="enterHandler"
      ref="myInput"
      autofocus
      size="lg"
      :state="state"
      v-model="value"
      placeholder="请输入上面的单词"
      class="mt-4"
      >
      </b-form-input>
    </div>
    <div :class="messageClass">{{message}}</div>
  </div>
</template>
<script>
export default {
  props: ['memory'],
  data() {
    return {
      value: '',
      state: null,
      message: 'Hello! 开始吧',
      messageClass: ['card-footer'],
    };
  },
  methods: {
    play() {
      this.$refs.audio.play();
    },
    enterHandler() {
      if (this.value === this.memory.english) {
        this.state = true;
        this.toggleClass('text-success');
        this.message = 'Nice! 下一个！';
        this.$refs.myInput.blur();
        setTimeout(() => {
          this.$emit('next');
        }, 500);
      } else {
        this.state = false;
        this.toggleClass('text-danger');
        this.message = '手误了吧？重新输入';
        this.value = '';
      }
    },
    toggleClass(classString) {
      if (this.messageClass.length > 1) {
        this.messageClass.pop();
        this.messageClass.push(classString);
      } else {
        this.messageClass.push(classString);
      }
    },
    focus() {
      this.$refs.myInput.focus();
    },
  },
};
</script>
