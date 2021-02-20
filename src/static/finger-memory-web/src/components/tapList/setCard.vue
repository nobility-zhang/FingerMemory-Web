<template>
  <b-card no-body class="overflow-hidden">
    <b-row no-gutters>
      <b-col md="6">
        <b-card-img :src="cardImpl.coveruUrl" class="rounded-0"></b-card-img>
      </b-col>
      <b-col md="6">
        <b-card-body :title="cardImpl.name">
          <b-row>
            <b-col md="3">
              <span>进度</span>
            </b-col>
            <b-col md="8" class="pt-1 pr-0 pl-0">
              <b-progress :max="cardImpl.max">
                <b-progress-bar :value="cardImpl.accomplish"
                :label="`${((cardImpl.accomplish / cardImpl.max) * 100).toFixed(2)}%`">
                </b-progress-bar>
              </b-progress>
            </b-col>
          </b-row>
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
