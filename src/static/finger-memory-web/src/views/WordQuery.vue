<template>
  <b-container class="bv-example-row">
    <search
    title="查找一个单词"
    placeholder="请输入要查询的单词"
    random-search="每日一词"
    type="word"
    ></search>
    <b-row>
      <b-col md="6">
        <b-card
          no-body
          style="max-width: 35rem"
          img-src="https://placekitten.com/380/200"
          img-alt="Image"
          img-top
        >
          <b-card-body>
            <b-card-title>英文</b-card-title>
            <b-card-sub-title class="mb-2">
              <div class="d-flex justify-content-between">
                <span>翻译</span>
                <b-link href="#">
                  <b-icon-volume-up-fill
                    font-scale="1.5"
                  ></b-icon-volume-up-fill>
                </b-link>
              </div>
            </b-card-sub-title>
            <b-card-text>
              解释解释解释解释解释解释解释解释解释解释解释解释解释解释解释解释
              解释解释解释解释解释解释解释解释解释解释解释解释解释解释解释解释
            </b-card-text>
          </b-card-body>
          <b-card-body class="mb-0 pb-0">
            <b-button variant="primary">添加笔记</b-button>
          </b-card-body>
          <b-card-body class="mt-0 pt-0">
            <a href="#" class="card-link">点赞</a>
            <a href="#" class="card-link">收藏</a>
          </b-card-body>
        </b-card>
      </b-col>
      <b-col md="6">
        <b-card header="联想词">
          <b-list-group>
            <b-list-group-item href="#" class="flex-column align-items-start">
              <div class="d-flex w-100 justify-content-between">
                <h5 class="mb-1">英文</h5>
                <b-link>
                  <b-icon-star font-scale="1.5"></b-icon-star>
                </b-link>
              </div>
              <span class="mb-1 pb-3">翻译</span>
              <p class="mb-1 mt-2">
                解释解释解释解释解释解释解释解释解释解释解释解释解释解释
                解释解释解释解释解释解释解释解释解释解释解释解释解释解释
              </p>
            </b-list-group-item>

            <b-list-group-item href="#" class="flex-column align-items-start">
              <div class="d-flex w-100 justify-content-between">
                <h5 class="mb-1">英文</h5>
                <b-link>
                  <b-icon-star font-scale="1.5"></b-icon-star>
                </b-link>
              </div>
              <span class="mb-1 pb-3">翻译</span>
              <p class="mb-1 mt-2">
                解释解释解释解释解释解释解释解释解释解释解释解释解释解释
                解释解释解释解释解释解释解释解释解释解释解释解释解释解释
              </p>
            </b-list-group-item>

            <b-list-group-item href="#" class="flex-column align-items-start">
              <div class="d-flex w-100 justify-content-between">
                <h5 class="mb-1">英文</h5>
                <b-link>
                  <b-icon-star font-scale="1.5"></b-icon-star>
                </b-link>
              </div>
              <span class="mb-1 pb-3">翻译</span>
              <p class="mb-1 mt-2">
                解释解释解释解释解释解释解释解释解释解释解释解释解释解释
                解释解释解释解释解释解释解释解释解释解释解释解释
              </p>
            </b-list-group-item>
          </b-list-group>
        </b-card>
      </b-col>
    </b-row>
  </b-container>
</template>
<script>
import search from '@/components/search.vue';

export default {
  components: { search },
};
</script>
