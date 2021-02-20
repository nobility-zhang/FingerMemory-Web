<template>
  <b-card header="联想词">
    <b-list-group>
      <b-list-group-item
      v-for="associate in associates"
      :key="associate.id"
      :href="associate.url"
      class="flex-column align-items-start">
        <div class="d-flex w-100 justify-content-between">
          <h5 class="mb-1">{{associate.english}}</h5>
          <b-link href="#">
            <b-icon-star font-scale="1.5"></b-icon-star>
          </b-link>
        </div>
        <span class="mb-1 pb-3">{{associate.translation}}</span>
        <p class="mb-1 mt-2">{{associate.description}}</p>
      </b-list-group-item>
    </b-list-group>
  </b-card>
</template>
<script>
export default {
  props: ['associates'],
};
</script>
