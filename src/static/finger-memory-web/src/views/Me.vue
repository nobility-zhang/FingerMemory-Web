<template>
<b-container class="bv-example-row">
  <b-row>
    <b-tabs content-class="mt-3">
      <b-tab title="我的卡组" active>
        <b-card no-body class="overflow-hidden" style="max-width: 540px;">
          <b-row no-gutters>
            <b-col md="6">
              <b-card-img src="https://picsum.photos/400/400/?image=20" class="rounded-0"></b-card-img>
            </b-col>
            <b-col md="6">
              <b-card-body title="卡组名">
                <b-row>
                  <b-col md="3">
                    <span>进度</span>
                  </b-col>
                  <b-col md="8" class="pt-1 pr-0 pl-0">
                    <b-progress max="100">
                      <b-progress-bar value="77"
                      :label="`${((77 / 100) * 100).toFixed(2)}%`">
                      </b-progress-bar>
                    </b-progress>
                  </b-col>
                </b-row>
                <b-card-text class="mt-3">
                  卡组描述卡组描述卡组描述卡组描述卡组描述卡组描述卡组描述卡组描述卡组描述卡组描述卡组描述
                  卡组描述卡组描述卡组描述卡组描述卡组描述卡组描述卡组描述卡组描述
                </b-card-text>
              </b-card-body>
            </b-col>
          </b-row>
        </b-card>
      </b-tab>
      <b-tab title="分类2">
        <p>I'm the second tab</p>
      </b-tab>
      <b-tab title="分类3">
        <p>I'm a disabled tab!</p>
      </b-tab>
    </b-tabs>
  </b-row>
  <b-row align-h="center">
    <div class="mt-3">
      <b-pagination v-model="currentPage" :total-rows="rows"></b-pagination>
    </div>
  </b-row>
  <b-row class="mt-5">
    <b-col md="6">
      <b-form-group
        id="username-group"
        label="原密码："
        label-for="username"
      >
        <b-form-input
          id="username"
          type="email"
          placeholder="请输入原密码"
          required
        ></b-form-input>
      </b-form-group>

      <b-form-group id="password-group-1" label="新密码：" label-for="password-1">
        <b-form-input
          type="password"
          id="password-1"
          placeholder="请输入密码"
          required
        ></b-form-input>
      </b-form-group>

      <b-form-group id="password-group-2" label="确认新密码：" label-for="password-2">
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
            <b-progress-bar value="77"
            :label="`${((77 / 100) * 100).toFixed(2)}%`">
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
            <b-progress-bar value="77"
            :label="`${((77 / 100) * 100).toFixed(2)}%`">
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
            <b-progress-bar value="77"
            :label="`${((77 / 100) * 100).toFixed(2)}%`">
            </b-progress-bar>
          </b-progress>
        </b-col>
      </b-row>
    </b-col>
  </b-row>
</b-container>
</template>
