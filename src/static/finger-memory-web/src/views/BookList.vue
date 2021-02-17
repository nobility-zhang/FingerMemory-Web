<template>
  <b-container class="bv-example-row">
    <search
    title="单词图书馆"
    placeholder="请输入要查询的图书"
    random-search="随机推荐"
    type="book"
    ></search>
    <b-row>
      <b-tabs content-class="mt-3">
        <b-tab title="分类1" active>
          <b-card no-body class="overflow-hidden" style="max-width: 540px;">
            <b-row no-gutters>
              <b-col md="6">
                <b-card-img src="https://picsum.photos/400/400/?image=20" class="rounded-0"></b-card-img>
              </b-col>
              <b-col md="6">
                <b-card-body title="图书名">
                  <b-card-text>
                    图书描述图书描述图书描述图书描述图书描述图书描述图书描述
                    图书描述图书描述图书描述图书描述图书描述图书描述图书描述
                    图书描述图书描述图书描述图书描述图书描述图书描述
                  </b-card-text>
                      <b-link href="#" class="card-link">作者名</b-link>
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
  </b-container>
</template>
<script>
import search from '@/components/search.vue';

export default {
  components: { search },
};
</script>
