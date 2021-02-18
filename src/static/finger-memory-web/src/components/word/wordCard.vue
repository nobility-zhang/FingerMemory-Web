<template>
  <div>
  <b-card
    no-body
    style="max-width: 35rem"
    :img-src="cardImpl.imageUrl"
    img-alt="word Image"
    img-top
  >
    <b-card-body>
      <b-card-title class="mb-4">{{cardImpl.english}}</b-card-title>
      <b-card-sub-title class="mb-2">
        <div class="d-flex justify-content-between">
          <span>{{cardImpl.translation}}</span>
          <b-link href="#">
            <b-icon-volume-up-fill font-scale="1.5"></b-icon-volume-up-fill>
          </b-link>
        </div>
      </b-card-sub-title>
      <b-card-text>{{cardImpl.description}}</b-card-text>
    </b-card-body>
    <b-card-body class="mb-0 pb-0">
      <b-button v-b-modal.note variant="primary">查看笔记</b-button>
    </b-card-body>
    <b-card-body class="mt-0 pt-0">
      <a href="#" class="card-link">点赞</a>
      <a href="#" class="card-link">收藏</a>
    </b-card-body>
  </b-card>
  <word-note></word-note>
  </div>
</template>
<script>
import wordNote from '@/components/word/wordNote.vue';

export default {
  props: ['card'],
  data() {
    return {
      cardImpl: this.card,
    };
  },
  components: {
    wordNote,
  },
};
</script>
