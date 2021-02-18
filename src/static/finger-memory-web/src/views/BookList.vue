<template>
  <b-container class="bv-example-row">
    <search
    title="单词图书馆"
    placeholder="请输入要查询的图书"
    random-search="随机推荐"
    type="book"
    ></search>
    <tap-list
    type="book"
    :list="list"
    >
    </tap-list>
  </b-container>
</template>
<script>
import search from '@/components/search.vue';
import tapList from '@/components/tapList.vue';

export default {
  data() {
    return {
      list: {
        '分 类 1': [
          {
            id: 1,
            coveruUrl: 'https://picsum.photos/400/400?image=20',
            name: '图书名1',
            description: '图书描述1',
            author: '图书作者1',
          },
          {
            id: 2,
            coveruUrl: 'https://picsum.photos/400/400?image=21',
            name: '图书名2',
            description: '图书描述2',
            author: '图书作者2',
          },
          {
            id: 3,
            coveruUrl: 'https://picsum.photos/400/400?image=31',
            name: '图书名3',
            description: '图书描述3',
            author: '图书作者3',
          },
          {
            id: 4,
            coveruUrl: 'https://picsum.photos/400/400?image=41',
            name: '图书名4',
            description: '图书描述4',
            author: '图书作者4',
          },
          {
            id: 5,
            coveruUrl: 'https://picsum.photos/400/400?image=51',
            name: '图书名5',
            description: '图书描述5',
            author: '图书作者5',
          },
          {
            id: 6,
            coveruUrl: 'https://picsum.photos/400/400?image=61',
            name: '图书名6',
            description: '图书描述6',
            author: '图书作者6',
          },
          {
            id: 7,
            coveruUrl: 'https://picsum.photos/400/400?image=71',
            name: '图书名7',
            description: '图书描述7',
            author: '图书作者7',
          },
        ],
        '分 类 2': [
          {
            id: 1,
            coveruUrl: 'https://picsum.photos/400/400?image=20',
            name: '图书名1',
            description: '图书描述1',
            author: '图书作者1',
          },
          {
            id: 2,
            coveruUrl: 'https://picsum.photos/400/400?image=21',
            name: '图书名2',
            description: '图书描述2',
            author: '图书作者2',
          },
          {
            id: 3,
            coveruUrl: 'https://picsum.photos/400/400?image=31',
            name: '图书名3',
            description: '图书描述3',
            author: '图书作者3',
          },
          {
            id: 4,
            coveruUrl: 'https://picsum.photos/400/400?image=41',
            name: '图书名4',
            description: '图书描述4',
            author: '图书作者4',
          },
          {
            id: 5,
            coveruUrl: 'https://picsum.photos/400/400?image=51',
            name: '图书名5',
            description: '图书描述5',
            author: '图书作者5',
          },
          {
            id: 6,
            coveruUrl: 'https://picsum.photos/400/400?image=61',
            name: '图书名6',
            description: '图书描述6',
            author: '图书作者6',
          },
          {
            id: 7,
            coveruUrl: 'https://picsum.photos/400/400?image=71',
            name: '图书名7',
            description: '图书描述7',
            author: '图书作者7',
          },
        ],
      },
    };
  },
  components: { search, tapList },
};
</script>
