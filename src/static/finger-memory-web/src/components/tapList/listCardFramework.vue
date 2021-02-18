<template>
  <div>
    <b-row v-if="(typeImpl == 'book')">
      <b-col md="4" v-for="item in cardListImpl" :key="item.id">
        <book-card :card="item" class="mt-3"></book-card>
      </b-col>
    </b-row>
    <b-row v-if="(typeImpl == 'set')">
      <b-col md="6" v-for="item in cardListImpl" :key="item.id">
        <set-card :card="item" class="mt-3"></set-card>
      </b-col>
    </b-row>
    <b-pagination
      v-model="currentPage"
      :total-rows="rows"
      :per-page="perPage"
      align="center"
      class="mt-3 overflow-auto"
    ></b-pagination>
  </div>
</template>
<script>
import bookCard from '@/components/tapList/bookCard.vue';
import setCard from '@/components/tapList/setCard.vue';

export default {
  props: ['cardList', 'type'],
  data() {
    return {
      typeImpl: this.type,
      currentPage: 1,
      perPage: 6,
      cardListImpl: [...this.cardList].splice(0, 6),
    };
  },
  watch: {
    currentPage(value) {
      const arr = [...this.cardList];
      this.cardListImpl = arr.splice(this.perPage * (value - 1), this.perPage);
    },
  },
  computed: {
    rows() {
      return this.cardList.length;
    },
  },
  components: {
    bookCard,
    setCard,
  },
};
</script>
