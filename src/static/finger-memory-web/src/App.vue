<template>
  <div>
    <div>
      <b-navbar type="dark" variant="dark">
        <b-navbar-brand href="/">logo</b-navbar-brand>
        <b-navbar-nav>
          <b-nav-item href="/">首页</b-nav-item>
          <b-nav-item href="/word-query">单词查询</b-nav-item>
          <b-nav-item href="/book-list">图书列表</b-nav-item>
          <b-nav-item href="/me">个人中心</b-nav-item>
        </b-navbar-nav>
      </b-navbar>
    </div>
    <router-view/>
  </div>
</template>
