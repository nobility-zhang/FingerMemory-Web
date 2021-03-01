/* eslint-disable */
import Vue from 'vue';
import axios from 'axios';
import _axiosJava from './axios-java';
import _axiosNode from './axios-node';

// Full config:  https://github.com/axios/axios#request-config
// axios.defaults.baseURL = process.env.baseURL || process.env.apiUrl || '';
// axios.defaults.headers.common['Authorization'] = AUTH_TOKEN;
// axios.defaults.headers.post['Content-Type'] = 'application/x-www-form-urlencoded';

const config = {
  // baseURL: process.env.baseURL || process.env.apiUrl || ""
  // timeout: 60 * 1000, // Timeout
  // withCredentials: true, // Check cross-site Access-Control
};

const _axios = axios.create(config);

_axios.interceptors.request.use(
  (config) =>
    // Do something before request is sent
    config,
  (error) =>
    // Do something with request error
    Promise.reject(error),

);

// Add a response interceptor
_axios.interceptors.response.use(
  (response) =>
    // Do something with response data
    response,
  (error) =>
    // Do something with response error
    Promise.reject(error),

);

Plugin.install = function (Vue, options) {
  Vue.axios = _axios;
  Vue.axiosJava = _axiosJava;
  Vue.axiosNode = _axiosNode;
  window.axios = _axios;
  window.axiosJava = _axiosJava;
  window.axiosNode = _axiosNode;
  Object.defineProperties(Vue.prototype, {
    axios: {
      get() {
        return _axios;
      },
    },
    axiosJava: {
      get() {
        return _axiosJava;
      },
    },
    axiosNode: {
      get() {
        return _axiosNode;
      },
    },
    $axios: {
      get() {
        return _axios;
      },
    },
    $axiosJava: {
      get() {
        return _axiosJava;
      },
    },
    $axiosNode: {
      get() {
        return _axiosNode;
      },
    },
  });
};

Vue.use(Plugin);

export default Plugin;
