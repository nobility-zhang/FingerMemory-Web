<template>
  <b-card no-body class="overflow-hidden">
    <b-row no-gutters>
      <b-col md="6">
        <b-card-img :src="cardImpl.coveruUrl" class="rounded-0"></b-card-img>
      </b-col>
      <b-col md="6">
        <b-card-body :title="cardImpl.name">
          <b-link href="#" class="card-link">{{cardImpl.author}}</b-link>
          <b-card-text class="mt-3">{{cardImpl.description}}</b-card-text>
        </b-card-body>
      </b-col>
    </b-row>
  </b-card>
</template>
<script>
export default {
  props: ['card'],
  data() {
    return {
      cardImpl: this.card,
    };
  },
};
</script>
