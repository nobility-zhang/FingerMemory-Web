<template>
  <div class="card border border-info">
    <div class="card-header text-center bg-info text-white">单选题</div>
    <div class="card-body text-left text-dark">
      <h6 class="card-title"><strong>问题：</strong>{{multipleChoice.issue}}</h6>
      <b-link v-b-toggle.collapse>查看译文</b-link>
      <b-collapse id="collapse">
        <p class="card-text"><strong>译文：</strong>{{multipleChoice.translated}}</p>
      </b-collapse>
      <b-form-group>
        <b-form-radio
          v-for="(item, index) in optionsImpl"
          :key="item.value"
          :state="item.right"
          v-model="selected"
          @change="judge(index)"
          class="mt-3"
          name="radios"
          :value="item.value"
        >
          {{item.value | prefix(index)}}
        </b-form-radio>
      </b-form-group>
    </div>
    <div class="card-footer text-dark">估分：{{score}}</div>
    <bingo :resolve="multipleChoice.resolve" :bingoId="bingoIdImpl"></bingo>
  </div>
</template>
<script>
import bingo from '@/components/core/bingo.vue';

export default {
  props: ['multipleChoice', 'bingoId'],
  data() {
    return {
      bingoIdImpl: this.bingoId,
      optionsImpl: this.stateNull(this.multipleChoice.options),
      score: 100,
      selected: '',
    };
  },
  methods: {
    stateNull(options) {
      const optionsCopy = [];
      options.forEach((item) => {
        const itemCopy = { ...item };
        itemCopy.right = null;
        optionsCopy.push(itemCopy);
      });
      return optionsCopy;
    },
    judge(index) {
      this.optionsImpl[index].right = this.multipleChoice.options[index].right;
      if (!this.multipleChoice.options[index].right) {
        this.score = this.score - this.oneScore > 0 ? this.score - this.oneScore : this.score;
      } else {
        this.$root.$bvToast.show(`toast-${this.bingoIdImpl}`);
      }
    },
  },
  computed: {
    oneScore() {
      return (1 / this.multipleChoice.options.length) * 100;
    },
  },
  filters: {
    prefix(value, index) {
      const string = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
      const array = string.slice('');
      return `${array[index]}、${value}`;
    },
  },
  components: {
    bingo,
  },
};
</script>
