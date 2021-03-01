/* eslint-disable */
import axios from 'axios';
const config = {
  baseURL: process.env.baseURL || process.env.apiUrl || "http://localhost:3000",
  timeout: 60 * 1000, // Timeout
  withCredentials: true, // Check cross-site Access-Control
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
export default _axios;
