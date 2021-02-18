<template>
  <div>
    <b-modal
      id="note"
      title="笔记"
    >
      <b-container fluid>
        <b-form-textarea
        v-if="write"
        placeholder="请输入你的笔记"
        v-model="text"
        rows="8"
        ></b-form-textarea>

        <b-form-textarea
        v-if="!write"
        placeholder="请输入你的笔记"
        v-model="text"
        rows="8"
        plaintext
        ></b-form-textarea>
      </b-container>

      <template #modal-footer>
        <div class="w-100">
          <b-button
          v-if="write"
          @click="update"
          variant="primary" class="float-right m-1">
          确认添加
          </b-button>
          <b-button
          v-if="!write"
          @click="update"
          variant="success" class="float-right m-1">
          进行修改
          </b-button>
        </div>
      </template>
    </b-modal>
  </div>
</template>
<script>
export default {
  data() {
    return {
      write: false,
      text: '',
    };
  },
  methods: {
    update() {
      this.write = !this.write;
    },
  },
};
</script>
