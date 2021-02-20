<template>
  <word-frameword>
    <template #1>
      <word-card :card="card">
      </word-card>
    </template>
    <template #2>
      <word-associate :associates="associates">
      </word-associate>
    </template>
  </word-frameword>
</template>
<script>
import wordFrameword from '@/components/word/wordFrameword.vue';
import wordAssociate from '@/components/word/wordAssociate.vue';
import wordCard from '@/components/word/wordCard.vue';
import { mapState } from 'vuex';

export default {
  computed: {
    ...mapState({
      associates: (state) => state.wordQuery.associates,
      card: (state) => state.wordQuery.card,
    }),
  },
  created() {
    this.$store.dispatch('wordQuery/getAssociates');
    this.$store.dispatch('wordQuery/getCard');
  },
  components: {
    wordFrameword,
    wordAssociate,
    wordCard,
  },
};
</script>
