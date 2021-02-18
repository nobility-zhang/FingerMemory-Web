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

export default {
  data() {
    return {
      associates: [
        {
          id: 1,
          url: '#',
          english: 'english',
          translation: '英文',
          description: `英格兰人的；英格兰的；
          英文的；英语；英格兰人；（台球中的）侧旋；
          把……译成英语；（美、加、英、澳）英格利希（人名）`,
        },
        {
          id: 2,
          url: '#',
          english: 'english',
          translation: '英文',
          description: `英格兰人的；英格兰的；
          英文的；英语；英格兰人；（台球中的）侧旋；
          把……译成英语；（美、加、英、澳）英格利希（人名）`,
        },
        {
          id: 3,
          url: '#',
          english: 'english',
          translation: '英文',
          description: `英格兰人的；英格兰的；
          英文的；英语；英格兰人；（台球中的）侧旋；
          把……译成英语；（美、加、英、澳）英格利希（人名）`,
        },
      ],
      card: {
        id: 1,
        imageUrl: 'https://placekitten.com/380/200',
        english: 'english',
        translation: '英文',
        description: `英格兰人的；英格兰的；
        英文的；英语；英格兰人；（台球中的）侧旋；
        把……译成英语；（美、加、英、澳）英格利希（人名）`,
      },
    };
  },
  components: {
    wordFrameword,
    wordAssociate,
    wordCard,
  },
};
</script>
