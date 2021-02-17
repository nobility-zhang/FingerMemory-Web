import Vue from 'vue';
import VueRouter from 'vue-router';
import Home from '../views/Home.vue';

Vue.use(VueRouter);

const routes = [
  {
    path: '/',
    name: 'Home',
    component: Home,
  },
  {
    path: '/me',
    name: 'Me',
    component: () => import(/* webpackChunkName: "me" */ '../views/Me.vue'),
  },
  {
    path: '/word-query',
    name: 'WordQuery',
    component: () => import(/* webpackChunkName: "word-query" */ '../views/WordQuery.vue'),
  },
  {
    path: '/book-list',
    name: 'BookList',
    component: () => import(/* webpackChunkName: "book-list" */ '../views/BookList.vue'),
  },
  {
    path: '/log-in',
    name: 'LogIn',
    component: () => import(/* webpackChunkName: "log-in" */ '../views/LogIn.vue'),
  },
  {
    path: '/sign-up',
    name: 'SignUp',
    component: () => import(/* webpackChunkName: "sign-up" */ '../views/SignUp.vue'),
  },
];

const router = new VueRouter({
  mode: 'history',
  base: process.env.BASE_URL,
  routes,
});

export default router;
