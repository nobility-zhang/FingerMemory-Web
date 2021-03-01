<template>
  <div>
    <h1 class="text-center">登入到 FingerMemory-Web</h1>
    <b-form>
      <b-form-group
        id="username-group"
        label="用户名或邮箱地址："
        label-for="username"
      >
        <b-form-input
          id="username"
          type="text"
          placeholder="请输入用户名或邮箱地址"
          v-model="account"
          required
        ></b-form-input>
      </b-form-group>

      <b-form-group id="password-group" label="密码：" label-for="password">
        <b-form-input
          type="password"
          id="password"
          placeholder="请输入密码"
          v-model="password"
          required
        ></b-form-input>
      </b-form-group>

      <b-form-group
      id="verification-group"
      label="验证码："
      label-for="verification">
        <div class="d-flex justify-content-between">
          <b-img src="https://lizonghang.github.io/images/authexam.jpg" alt="Responsive image"></b-img>
          <b-link href="#">看不清，换一张</b-link>
        </div>
        <b-form-input
          id="verification"
          placeholder="请输入验证码"
          required
        ></b-form-input>
      </b-form-group>

      <b-form-group id="check-group">
        <b-form-checkbox-group id="checkboxes">
          <b-form-checkbox value="me">记住我</b-form-checkbox>
        </b-form-checkbox-group>
      </b-form-group>

      <b-button type="submit" block variant="primary" @click="login">登入</b-button>

      <div class="d-flex justify-content-center">
        <span>还没有账号？</span>
        <b-link href="#/sign-up">只需5秒，快速注册</b-link>
      </div>
    </b-form>
  </div>
</template>
<script>
export default {
  data() {
    return {
      password: '',
      account: '',
    };
  },
  methods: {
    login() {
      this.$store.dispatch('login/getUserInfo', {
        account: this.account,
        password: this.password,
      });
    },
  },
};
</script>
