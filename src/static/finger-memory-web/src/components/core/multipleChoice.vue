<template>
  <div class="card border border-info">
    <div class="card-header text-center bg-info text-white">单选题</div>
    <div class="card-body text-left text-dark">
      <h6 class="card-title"><strong>问题：</strong>{{issue}}</h6>
      <b-link v-b-toggle.collapse>查看译文</b-link>
      <b-collapse id="collapse">
        <p class="card-text"><strong>译文：</strong>{{translated}}</p>
      </b-collapse>
      <b-form-group>
        <b-form-radio
          v-for="(item, index) in optionsImpl"
          :key="item.value"
          :state="item.reight"
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
    <bingo :resolve="resolveImpl" :bingoId="bingoIdImpl"></bingo>
  </div>
</template>
<script>
import bingo from '@/components/core/bingo.vue';

export default {
  props: ['issue', 'translated', 'options', 'resolve', 'bingoId'],
  data() {
    return {
      bingoIdImpl: this.bingoId,
      issueImpl: this.issue,
      translatedImpl: this.translated,
      resolveImpl: this.resolve,
      optionsImpl: this.stateNull(this.options),
      score: 100,
      selected: '',
    };
  },
  methods: {
    stateNull(options) {
      const optionsCopy = [];
      options.forEach((item) => {
        const itemCopy = { ...item };
        itemCopy.reight = null;
        optionsCopy.push(itemCopy);
      });
      return optionsCopy;
    },
    judge(index) {
      this.optionsImpl[index].reight = this.options[index].reight;
      if (!this.options[index].reight) {
        this.score = this.score - this.oneScore > 0 ? this.score - this.oneScore : this.score;
      } else {
        this.$root.$bvToast.show(`toast-${this.bingoIdImpl}`);
      }
    },
  },
  computed: {
    oneScore() {
      return (1 / this.options.length) * 100;
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
