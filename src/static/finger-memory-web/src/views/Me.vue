<template>
  <b-container class="bv-example-row">
    <tap-list
    type="set"
    :list="list"
    >
      <b-tab title="个人信息">
        <b-row class="mt-5">
          <b-col md="6">
            <b-form-group id="username-group" label="原密码：" label-for="username">
              <b-form-input
                id="username"
                type="email"
                placeholder="请输入原密码"
                required
              ></b-form-input>
            </b-form-group>

            <b-form-group
              id="password-group-1"
              label="新密码："
              label-for="password-1"
            >
              <b-form-input
                type="password"
                id="password-1"
                placeholder="请输入密码"
                required
              ></b-form-input>
            </b-form-group>

            <b-form-group
              id="password-group-2"
              label="确认新密码："
              label-for="password-2"
            >
              <b-form-input
                type="password"
                id="password"
                placeholder="请重新输入密码"
                required
              ></b-form-input>
            </b-form-group>
            <b-button type="submit" block variant="primary">修改</b-button>
          </b-col>
          <b-col md="6">
            <b-row class="alert alert-primary" role="alert">
              <strong>提示：</strong>点击计划进行修改
            </b-row>
            <b-row class="h-25 border border-primary rounded pt-4 mb-2 shadow-sm">
              <b-col md="2">
                <span>卡组名</span>
              </b-col>
              <b-col md="10" class="pt-1 pr-0 pl-0">
                <b-progress max="100">
                  <b-progress-bar
                    value="77"
                    :label="`${((77 / 100) * 100).toFixed(2)}%`"
                  >
                  </b-progress-bar>
                </b-progress>
              </b-col>
            </b-row>
            <b-row class="h-25 border border-primary rounded pt-4 mb-2 shadow-sm">
              <b-col md="2">
                <span>卡组名</span>
              </b-col>
              <b-col md="10" class="pt-1 pr-0 pl-0">
                <b-progress max="100">
                  <b-progress-bar
                    value="77"
                    :label="`${((77 / 100) * 100).toFixed(2)}%`"
                  >
                  </b-progress-bar>
                </b-progress>
              </b-col>
            </b-row>
            <b-row class="h-25 border border-primary rounded pt-4 mb-2 shadow-sm">
              <b-col md="2">
                <span>卡组名</span>
              </b-col>
              <b-col md="10" class="pt-1 pr-0 pl-0">
                <b-progress max="100">
                  <b-progress-bar
                    value="77"
                    :label="`${((77 / 100) * 100).toFixed(2)}%`"
                  >
                  </b-progress-bar>
                </b-progress>
              </b-col>
            </b-row>
          </b-col>
        </b-row>
      </b-tab>
    </tap-list>
  </b-container>
</template>
<script>
import tapList from '@/components/tapList.vue';
import { mapState } from 'vuex';

export default {
  computed: {
    ...mapState({
      list: (state) => state.me.list,
    }),
  },
  created() {
    this.$store.dispatch('me/getList');
  },
  components: { tapList },
};
</script>
