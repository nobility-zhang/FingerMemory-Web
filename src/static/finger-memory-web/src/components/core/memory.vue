<template>
  <div class="card border border-info">
    <div class="card-header text-center bg-info text-white">记忆卡片</div>
    <div class="card-body text-center text-dark">
      <h2 class="card-title">{{ word }}</h2>
      <b-link href="#" > <strong>{{ translation }}</strong> </b-link>
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
  data() {
    return {
      word: 'word',
      translation: '单词',
      value: '',
      state: null,
      message: 'Hello! 开始吧',
      messageClass: ['card-footer'],
    };
  },
  methods: {
    enterHandler() {
      if (this.value === this.word) {
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
