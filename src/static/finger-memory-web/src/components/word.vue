<template>
  <b-row>
    <b-col md="6" v-for="item in card" :key="item.id">
      <word-card :card="item"></word-card>
    </b-col>
    <b-col md="6" v-if="card.length === 1">
      <word-associate :associates="associates"></word-associate>
    </b-col>
  </b-row>
</template>
<script>
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
    wordAssociate,
    wordCard,
  },
};
</script>
