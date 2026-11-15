<script setup lang="ts">
import { onMounted, ref } from 'vue';
import type {
  FingerMemoryRepository,
  InAppReminderRecord,
  InAppReminderSettings,
} from '../core/repository';
import { resetReminderSchedule } from './reminderPolicy';

const props = defineProps<{
  repository: FingerMemoryRepository;
}>();

const record = ref<InAppReminderRecord>();
const form = ref<InAppReminderSettings>();
const loading = ref(true);
const saving = ref(false);
const error = ref<string | null>(null);
const savedMessage = ref<string | null>(null);

async function load(): Promise<void> {
  loading.value = true;
  error.value = null;
  try {
    const loaded = await props.repository.getInAppReminderRecord();
    record.value = loaded;
    form.value = { ...loaded.settings };
  } catch {
    error.value = '无法读取提醒设置。';
  } finally {
    loading.value = false;
  }
}

async function save(): Promise<void> {
  const currentRecord = record.value;
  const settings = form.value;
  if (currentRecord === undefined || settings === undefined) {
    return;
  }
  saving.value = true;
  error.value = null;
  savedMessage.value = null;
  try {
    const at = Date.now();
    const next = resetReminderSchedule(
      {
        settings: { ...settings },
        state: { ...currentRecord.state },
      },
      at,
    );
    record.value = await props.repository.saveInAppReminderRecord(next, at);
    form.value = { ...record.value.settings };
    savedMessage.value = '提醒设置已保存。';
    window.dispatchEvent(new Event('finger-memory:reminder-settings-changed'));
  } catch {
    error.value = '提醒设置无效或未能保存。';
  } finally {
    saving.value = false;
  }
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="reminder-settings" aria-labelledby="reminder-settings-title">
    <div class="settings-copy">
      <p class="lobby-kicker">应用内提醒</p>
      <h3 id="reminder-settings-title">练习提醒策略</h3>
      <p>仅在应用打开时生效。</p>
    </div>

    <div v-if="loading" class="settings-state" role="status">正在读取提醒设置...</div>
    <form v-else-if="form" class="reminder-form" @submit.prevent="save">
      <label class="enabled-field">
        <input v-model="form.enabled" type="checkbox" />
        <span>启用提醒</span>
      </label>
      <label>
        <span>最短间隔（分钟）</span>
        <input
          v-model.number="form.minIntervalMinutes"
          type="number"
          min="1"
          max="720"
          step="1"
        />
      </label>
      <label>
        <span>最长间隔（分钟）</span>
        <input
          v-model.number="form.maxIntervalMinutes"
          type="number"
          min="1"
          max="720"
          step="1"
        />
      </label>
      <label>
        <span>每日上限</span>
        <input
          v-model.number="form.dailyLimit"
          type="number"
          min="1"
          max="20"
          step="1"
        />
      </label>
      <label>
        <span>免打扰开始</span>
        <input v-model="form.quietStart" type="time" />
      </label>
      <label>
        <span>免打扰结束</span>
        <input v-model="form.quietEnd" type="time" />
      </label>
      <label>
        <span>稍后（分钟）</span>
        <input
          v-model.number="form.snoozeMinutes"
          type="number"
          min="1"
          max="240"
          step="1"
        />
      </label>
      <div class="form-actions">
        <button type="submit" :disabled="saving">保存提醒设置</button>
        <span v-if="savedMessage" role="status">{{ savedMessage }}</span>
        <span v-if="error" class="error-message" role="alert">{{ error }}</span>
      </div>
    </form>
  </section>
</template>

<style scoped>
.reminder-settings {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(300px, 1.3fr);
  gap: 20px 28px;
  padding: 18px;
  border: 1px solid #d3dcd6;
  border-radius: 8px;
  background: #fbfcfb;
}

.settings-copy h3 {
  margin: 0 0 6px;
  color: #263c30;
  font-size: 1.05rem;
}

.settings-copy p:last-child {
  margin: 0;
  color: #6d7c73;
  font-size: 0.78rem;
}

.reminder-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.reminder-form label {
  display: grid;
  gap: 5px;
  color: #586960;
  font-size: 0.72rem;
  font-weight: 700;
}

.reminder-form input[type="number"],
.reminder-form input[type="time"] {
  width: 100%;
  min-height: 38px;
  padding: 7px 9px;
  border: 1px solid #b8c5bd;
  border-radius: 6px;
  background: #fff;
  color: #263b30;
}

.reminder-form .enabled-field {
  display: flex;
  grid-column: 1 / -1;
  align-items: center;
  gap: 8px;
}

.enabled-field input {
  width: 16px;
  height: 16px;
}

.form-actions {
  display: flex;
  grid-column: 1 / -1;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.form-actions button {
  min-height: 38px;
  padding: 8px 12px;
  border: 1px solid #345b48;
  border-radius: 6px;
  background: #345b48;
  color: #fff;
  font-weight: 650;
  cursor: pointer;
}

.form-actions button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.form-actions span {
  color: #4e695b;
  font-size: 0.75rem;
}

.form-actions .error-message {
  color: #9e3e2c;
}

.settings-state {
  color: #718078;
  font-size: 0.8rem;
}

@media (max-width: 720px) {
  .reminder-settings,
  .reminder-form {
    grid-template-columns: 1fr;
  }
}
</style>
