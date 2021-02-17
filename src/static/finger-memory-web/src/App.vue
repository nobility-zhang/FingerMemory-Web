<template>
  <div class="container">
    <!-- navbar start-->
    <b-navbar toggleable="lg" type="dark" variant="dark">
      <!-- log start-->
      <b-navbar-brand href="/">Finger Memory</b-navbar-brand>
      <!-- log end-->
      <b-navbar-toggle target="nav-collapse"></b-navbar-toggle>
      <b-collapse id="nav-collapse" is-nav>
        <!-- nav start-->
        <b-navbar-nav>
          <b-nav-item href="/">首页</b-nav-item>
          <b-nav-item href="/word-query">单词查询</b-nav-item>
          <b-nav-item href="/book-list">图书列表</b-nav-item>
          <b-nav-item href="/me">个人中心</b-nav-item>
        </b-navbar-nav>
        <!-- nav start-->
        <!-- 已登入 start-->
        <!-- <b-navbar-nav class="ml-auto">
          <b-nav-item-dropdown right>
            <template #button-content>
              <b-img
                rounded="circle"
                src="https://placekitten.com/g/30/30"
                alt="Circle image"
              ></b-img>
              <em>User</em>
            </template>
            <b-dropdown-item href="#">Sign Out</b-dropdown-item>
          </b-nav-item-dropdown>
        </b-navbar-nav> -->
        <!-- 已登入 end-->
        <!-- 未登入 start -->
        <b-navbar-nav class="ml-auto">
            <b-button-group>
              <b-button href="/log-in" variant="outline-light">Log In</b-button>
              <b-button href="/sign-up" variant="outline-success">Sign Up</b-button>
            </b-button-group>
          </b-navbar-nav>
        <!-- 未登入 end -->
      </b-collapse>
    </b-navbar>
    <!-- navbar end-->
    <router-view />
  </div>
</template>
