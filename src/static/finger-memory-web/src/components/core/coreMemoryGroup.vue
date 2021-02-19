<template>
  <div>
    <b-carousel
      ref="myCarousel"
      v-model="slide"
      :interval="0"
      controls
      indicators
      no-wrap
      @sliding-start="onSlideStart"
      @sliding-end="onSlideEnd"
    >
      <b-carousel-slide class="bg-dark" v-for="item in 6" :key="item">
        <template #img>
          <b-row class="mb-5 mt-2">
            <b-col md="6" offset-md="3">
              <memory @next="next" ref="memory"></memory>
            </b-col>
          </b-row>
        </template>
      </b-carousel-slide>
    </b-carousel>
  </div>
</template>
<script>
import memory from '@/components/core/memory.vue';

export default {
  data() {
    return {
      issue: `Nine in ten parents said there were significant 
      differences in their approach to educating their children 
      compared with __ of their parents.`,
      translated: '十分之九的父母说他们在教育孩子的方法上与他们的父母有很大的不同',
      resolve: ` A选项几乎与原文一样。但是通过仔细阅读便会发现，作者吃惊的原因并不是
      因为盲人妇女自己画了一个圈，而是因为 To show this motion, she traced
      为了显示这种运动，她用在圈里面画了一个曲线。`,
      options: [
        { value: 'those', reight: false },
        { value: 'one', reight: false },
        { value: 'both', reight: true },
        { value: 'that', reight: false },
      ],
      slide: 0,
    };
  },
  methods: {
    onSlideStart(slide) {
      this.$root.$bvToast.hide(`toast-${slide}`);
    },
    onSlideEnd(slide) {
      this.$refs.memory[slide].focus();
    },
    next() {
      this.$refs.myCarousel.next();
    },
  },
  components: {
    memory,
  },
};
</script>
