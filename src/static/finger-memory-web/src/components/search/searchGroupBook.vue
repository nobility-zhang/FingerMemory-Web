<template>
  <searchFramework>
    <template #1-1>
    </template>
    <template #2-1>
      <search-input title="前缀" placeholder="请输入单词前缀"></search-input>
    </template>
    <template #1-2>
      <search-select title="分类"
      :options="['计算机史', '编程语言']">
      </search-select>
    </template>
    <template #2-2>
      <search-input title="包含" placeholder="请输入包含单词"></search-input>
    </template>
    <template #1-3>
    </template>
    <template #2-3>
      <search-input title="后缀" placeholder="请输入单词后缀"></search-input>
    </template>
  </searchFramework>
</template>
<script>
import searchSelect from '@/components/search/searchSelect.vue';
import searchInput from '@/components/search/searchInput.vue';
import searchFramework from '@/components/search/searchFramework.vue';

export default {
  components: {
    searchSelect,
    searchInput,
    searchFramework,
  },
};
</script>
