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
      <b-carousel-slide
      class="bg-dark"
      v-for="memory in memorys"
      :key="memory.id"
      >
        <template #img>
          <b-row class="mb-5 mt-2">
            <b-col md="6" offset-md="3">
              <memory @next="next" ref="memory" :memory="memory"></memory>
            </b-col>
          </b-row>
        </template>
      </b-carousel-slide>
    </b-carousel>
  </div>
</template>
<script>
import memory from '@/components/core/memory.vue';
import { mapState } from 'vuex';

export default {
  data() {
    return {
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
  computed: {
    ...mapState({
      memorys: (state) => state.home.memorys,
    }),
  },
  created() {
    this.$store.dispatch('home/getMemorys');
  },
  components: {
    memory,
  },
};
</script>
