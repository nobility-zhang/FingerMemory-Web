<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import type { FingerMemoryRepository } from '../core/repository';
import {
  deferReminder,
  dismissReminderForDay,
  isReminderDue,
  markReminderShown,
  prepareReminderRecord,
  snoozeReminder,
} from './reminderPolicy';

const props = withDefaults(
  defineProps<{
    repository: FingerMemoryRepository;
    clock?: () => number;
  }>(),
  {
    clock: () => Date.now(),
  },
);

const emit = defineEmits<{
  openPractice: [];
}>();

const notification = ref<{ count: number; snoozeMinutes: number } | null>(null);
let timer: ReturnType<typeof setTimeout> | undefined;
let disposed = false;

function currentTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
}

function clearTimer(): void {
  if (timer !== undefined) {
    clearTimeout(timer);
    timer = undefined;
  }
}

function schedule(nextAt: number | null): void {
  clearTimer();
  if (nextAt === null || disposed) {
    return;
  }
  const delay = Math.max(1_000, Math.min(nextAt - props.clock(), 24 * 60 * 60_000));
  timer = setTimeout(() => {
    void evaluateReminder();
  }, delay);
}

async function evaluateReminder(): Promise<void> {
  if (disposed) {
    return;
  }
  clearTimer();
  try {
    const at = props.clock();
    const timeZone = currentTimeZone();
    const stored = await props.repository.getInAppReminderRecord();
    let record = prepareReminderRecord(stored, at, timeZone);
    if (JSON.stringify(record) !== JSON.stringify(stored)) {
      record = await props.repository.saveInAppReminderRecord(record, at);
    }
    if (!isReminderDue(record, at, timeZone)) {
      notification.value = null;
      schedule(record.state.nextAt);
      return;
    }

    const plan = await props.repository.planPracticeQueue({ at, timeZone });
    if (plan.selected.length === 0) {
      const deferred = deferReminder(record, at);
      await props.repository.saveInAppReminderRecord(deferred, at);
      schedule(deferred.state.nextAt);
      return;
    }

    const shown = markReminderShown(record, at);
    await props.repository.saveInAppReminderRecord(shown, at);
    notification.value = {
      count: plan.selected.length,
      snoozeMinutes: shown.settings.snoozeMinutes,
    };
    schedule(shown.state.nextAt);
  } catch {
    schedule(props.clock() + 60_000);
  }
}

async function snooze(): Promise<void> {
  const at = props.clock();
  const stored = await props.repository.getInAppReminderRecord();
  const next = snoozeReminder(stored, at);
  await props.repository.saveInAppReminderRecord(next, at);
  notification.value = null;
  schedule(next.state.nextAt);
}

async function dismissToday(): Promise<void> {
  const at = props.clock();
  const stored = await props.repository.getInAppReminderRecord();
  const next = dismissReminderForDay(stored);
  await props.repository.saveInAppReminderRecord(next, at);
  notification.value = null;
  schedule(null);
}

function openPractice(): void {
  notification.value = null;
  emit('openPractice');
}

function handleSettingsChanged(): void {
  void evaluateReminder();
}

onMounted(() => {
  window.addEventListener('finger-memory:reminder-settings-changed', handleSettingsChanged);
  void evaluateReminder();
});

onBeforeUnmount(() => {
  disposed = true;
  clearTimer();
  window.removeEventListener(
    'finger-memory:reminder-settings-changed',
    handleSettingsChanged,
  );
});
</script>

<template>
  <aside v-if="notification" class="reminder-banner" role="status" aria-live="polite">
    <div>
      <strong>练习提醒</strong>
      <p>当前有 {{ notification.count }} 个目标可练习。</p>
    </div>
    <div class="reminder-actions">
      <button type="button" class="primary-action" @click="openPractice">去练习</button>
      <button type="button" @click="snooze">稍后 {{ notification.snoozeMinutes }} 分钟</button>
      <button type="button" @click="dismissToday">今天不再提醒</button>
    </div>
  </aside>
</template>

<style scoped>
.reminder-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 20px;
  padding: 14px 16px;
  border: 1px solid #c7b58d;
  border-radius: 8px;
  background: #fbf7ec;
}

.reminder-banner strong {
  color: #4a402b;
  font-size: 0.82rem;
}

.reminder-banner p {
  margin: 4px 0 0;
  color: #71664e;
  font-size: 0.8rem;
}

.reminder-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

button {
  min-height: 36px;
  padding: 7px 11px;
  border: 1px solid #c8bda4;
  border-radius: 6px;
  background: #fff;
  color: #4b4638;
  font-size: 0.76rem;
  font-weight: 650;
  cursor: pointer;
}

.primary-action {
  border-color: #345b48;
  background: #345b48;
  color: #fff;
}

button:focus-visible {
  outline: 3px solid rgb(45 113 83 / 28%);
  outline-offset: 2px;
}

@media (max-width: 720px) {
  .reminder-banner,
  .reminder-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .reminder-actions button {
    width: 100%;
  }
}
</style>
