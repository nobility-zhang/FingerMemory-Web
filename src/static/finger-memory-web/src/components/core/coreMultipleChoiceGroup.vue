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
    >
      <b-carousel-slide
      class="bg-dark"
      v-for="multipleChoice in multipleChoices"
      :key="multipleChoice.id">
        <template #img>
          <b-row class="mb-5 mt-2">
            <b-col md="6" offset-md="3">
              <multiple-Choice
              :multipleChoice="multipleChoice"
              :bingoId="multipleChoice.id"
              ></multiple-Choice>
            </b-col>
          </b-row>
        </template>
      </b-carousel-slide>
    </b-carousel>
  </div>
</template>
<script>
import multipleChoice from '@/components/core/multipleChoice.vue';
import { mapState } from 'vuex';

export default {
  data() {
    return {
      slide: 0,
    };
  },
  computed: {
    ...mapState({
      multipleChoices: (state) => state.home.multipleChoices,
    }),
  },
  created() {
    this.$store.dispatch('home/getMultipleChoices', { bookId: 0 });
  },
  methods: {
    onSlideStart() {
      this.multipleChoices.forEach(({ id }) => {
        this.$root.$bvToast.hide(`toast-${id}`);
      });
    },
  },
  components: {
    multipleChoice,
  },
};
</script>
