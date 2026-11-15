<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import type { ReviewRating } from '../core/domain';
import { formatDueLabel } from '../core/due';
import type {
  FingerMemoryRepository,
  ReviewHistoryEntry,
} from '../core/repository';

const props = defineProps<{
  repository: FingerMemoryRepository;
}>();

const history = ref<ReviewHistoryEntry[]>([]);
const loading = ref(true);
const error = ref<string | null>(null);
const now = ref(Date.now());

const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
const inconsistentCount = computed(
  () => history.value.filter((entry) => !entry.consistent).length,
);
const totalLogCount = computed(
  () => history.value.reduce((total, entry) => total + entry.logs.length, 0),
);

const ratingLabels: Record<ReviewRating, string> = {
  again: 'Again',
  hard: 'Hard',
  good: 'Good',
  easy: 'Easy',
};

function formatTimestamp(timestamp: number): string {
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(new Date(timestamp));
}

function formatCardDue(timestamp: number | null): string {
  return timestamp === null ? '首次复习' : formatDueLabel(timestamp, now.value, timeZone);
}

async function loadHistory(): Promise<void> {
  loading.value = true;
  error.value = null;
  now.value = Date.now();
  try {
    history.value = await props.repository.listReviewHistory();
  } catch {
    error.value = '无法读取正式复习诊断数据。';
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void loadHistory();
});
</script>

<template>
  <section class="review-workspace" aria-labelledby="review-workspace-title">
    <div class="review-intro">
      <div>
        <p class="section-label">正式复习</p>
        <h2 id="review-workspace-title">调度状态与提交日志</h2>
        <p>每个学习目标只有一份当前计划；日志按提交时间展开，可用提交 ID 定位到具体写入。</p>
      </div>
      <button type="button" :disabled="loading" @click="loadHistory">刷新诊断</button>
    </div>

    <div v-if="loading" class="review-state" role="status">正在读取正式复习记录...</div>
    <div v-else-if="error" class="review-state review-state--error" role="alert">
      <p>{{ error }}</p>
      <button type="button" @click="loadHistory">重试</button>
    </div>
    <template v-else>
      <dl class="review-metrics">
        <div>
          <dt>已有计划</dt>
          <dd>{{ history.length }}</dd>
        </div>
        <div>
          <dt>正式日志</dt>
          <dd>{{ totalLogCount }}</dd>
        </div>
        <div>
          <dt>一致性异常</dt>
          <dd :class="{ alert: inconsistentCount > 0 }">{{ inconsistentCount }}</dd>
        </div>
      </dl>

      <div v-if="history.length === 0" class="review-state">
        还没有正式复习。只有用户在隐藏答案、无提示、无揭晓时明确评分，才会产生这里的记录。
      </div>
      <ol v-else class="review-list">
        <li v-for="entry in history" :key="entry.studyItem.id">
          <article class="review-entry">
            <header>
              <div>
                <p class="entry-meaning">{{ entry.wordEntry.meaning }}</p>
                <h3>{{ entry.wordEntry.term }}</h3>
              </div>
              <span
                class="consistency-badge"
                :class="{ 'consistency-badge--error': !entry.consistent }"
              >
                {{ entry.consistent ? '状态与日志一致' : '需要检查' }}
              </span>
            </header>

            <dl class="state-grid">
              <div>
                <dt>当前到期</dt>
                <dd>{{ formatDueLabel(entry.state.dueAt, now, timeZone) }}</dd>
              </div>
              <div>
                <dt>调度状态</dt>
                <dd>{{ entry.state.scheduleStatus }} · {{ entry.state.state }}</dd>
              </div>
              <div>
                <dt>复习次数</dt>
                <dd>{{ entry.state.reps }} 次 · 遗忘 {{ entry.state.lapses }}</dd>
              </div>
              <div>
                <dt>模型</dt>
                <dd>{{ entry.state.schedulerModelVersion.toUpperCase() }}</dd>
              </div>
            </dl>

            <p v-if="entry.issue" class="diagnostic-alert" role="alert">{{ entry.issue }}</p>

            <details>
              <summary>查看 {{ entry.logs.length }} 条正式复习日志</summary>
              <ol class="log-list">
                <li v-for="log in entry.logs" :key="log.submissionId">
                  <div class="log-heading">
                    <strong>{{ ratingLabels[log.rating] }}</strong>
                    <time :datetime="new Date(log.reviewedAt).toISOString()">
                      {{ formatTimestamp(log.reviewedAt) }}
                    </time>
                  </div>
                  <code>{{ log.submissionId }}</code>
                  <dl class="log-grid">
                    <div>
                      <dt>来源模式</dt>
                      <dd>{{ log.modeId }}</dd>
                    </div>
                    <div>
                      <dt>先前后到期</dt>
                      <dd>{{ formatCardDue(log.beforeCard?.dueAt ?? null) }}</dd>
                    </div>
                    <div>
                      <dt>本次后到期</dt>
                      <dd>{{ formatDueLabel(log.afterCard.dueAt, now, timeZone) }}</dd>
                    </div>
                    <div>
                      <dt>证据</dt>
                      <dd>
                        隐藏 {{ log.answerWasHidden ? '是' : '否' }} · 提示
                        {{ log.hintUsed ? '是' : '否' }} · 揭晓
                        {{ log.answerRevealed ? '是' : '否' }}
                      </dd>
                    </div>
                    <div>
                      <dt>输入匹配</dt>
                      <dd>{{ log.inputMatchedAnswer ? '匹配' : '不匹配' }}</dd>
                    </div>
                    <div>
                      <dt>调度版本</dt>
                      <dd>
                        {{ log.schedulerPackageVersion }} · {{ log.schedulerModelVersion }}
                      </dd>
                    </div>
                  </dl>
                </li>
              </ol>
            </details>
          </article>
        </li>
      </ol>
    </template>
  </section>
</template>

<style scoped>
.review-workspace {
  display: grid;
  gap: 18px;
}

.review-intro {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  padding-bottom: 18px;
  border-bottom: 1px solid #d6ded9;
}

.review-intro h2 {
  margin: 0 0 8px;
  color: #21352a;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 1.9rem;
  font-weight: 500;
}

.review-intro p:last-child {
  max-width: 68ch;
  margin: 0;
  color: #68776f;
  line-height: 1.65;
}

button {
  min-height: 38px;
  padding: 8px 13px;
  border: 1px solid #bfcbc4;
  border-radius: 6px;
  background: #fff;
  color: #30453a;
  font-weight: 650;
  cursor: pointer;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

button:focus-visible,
summary:focus-visible {
  outline: 3px solid rgb(45 113 83 / 28%);
  outline-offset: 2px;
}

.review-state {
  padding: 22px;
  border: 1px solid #d3dcd6;
  border-radius: 8px;
  color: #66756d;
  background: #fbfcfb;
  line-height: 1.7;
}

.review-state--error {
  color: #9a432f;
}

.review-metrics,
.state-grid,
.log-grid {
  display: grid;
  gap: 1px;
  margin: 0;
  background: #dbe2de;
}

.review-metrics {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border: 1px solid #dbe2de;
  border-radius: 8px;
  overflow: hidden;
}

.review-metrics div,
.state-grid div,
.log-grid div {
  min-width: 0;
  padding: 12px 14px;
  background: #fff;
}

.review-metrics dt,
.state-grid dt,
.log-grid dt {
  color: #738078;
  font-size: 0.7rem;
  font-weight: 700;
}

.review-metrics dd,
.state-grid dd,
.log-grid dd {
  margin: 5px 0 0;
  color: #263b30;
  font-size: 0.82rem;
  overflow-wrap: anywhere;
}

.review-metrics dd {
  font-family: Georgia, "Times New Roman", serif;
  font-size: 1.7rem;
}

.review-metrics dd.alert {
  color: #a14a33;
}

.review-list,
.log-list {
  display: grid;
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.review-entry {
  padding: 18px;
  border: 1px solid #d3dcd6;
  border-radius: 8px;
  background: #fbfcfb;
}

.review-entry > header,
.log-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.entry-meaning {
  margin: 0 0 3px;
  color: #6e7d75;
  font-size: 0.75rem;
}

.review-entry h3 {
  margin: 0;
  color: #1e3328;
  font-size: 1.35rem;
}

.consistency-badge {
  flex-shrink: 0;
  padding: 5px 8px;
  border-radius: 5px;
  background: #e6efe9;
  color: #315846;
  font-size: 0.7rem;
  font-weight: 700;
}

.consistency-badge--error {
  background: #f8e9e4;
  color: #98442f;
}

.state-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-top: 14px;
  border: 1px solid #dbe2de;
  border-radius: 6px;
  overflow: hidden;
}

.diagnostic-alert {
  margin: 12px 0 0;
  padding: 10px 12px;
  border-left: 3px solid #ad5b42;
  background: #fbf1ed;
  color: #8d4634;
  font-size: 0.78rem;
}

details {
  margin-top: 14px;
}

summary {
  color: #3e6251;
  font-size: 0.78rem;
  font-weight: 700;
  cursor: pointer;
}

.log-list {
  margin-top: 10px;
}

.log-list > li {
  padding: 13px;
  border: 1px solid #d7dfda;
  border-radius: 6px;
  background: #fff;
}

.log-heading strong {
  color: #2d4c3d;
}

.log-heading time {
  color: #738078;
  font-size: 0.72rem;
}

code {
  display: block;
  margin: 6px 0 10px;
  color: #5b6b62;
  font-size: 0.72rem;
  overflow-wrap: anywhere;
}

.log-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border: 1px solid #dbe2de;
  border-radius: 5px;
  overflow: hidden;
}

@media (max-width: 720px) {
  .review-intro {
    flex-direction: column;
  }

  .review-metrics,
  .state-grid,
  .log-grid {
    grid-template-columns: 1fr;
  }

  .review-entry > header,
  .log-heading {
    flex-direction: column;
    gap: 8px;
  }
}
</style>
