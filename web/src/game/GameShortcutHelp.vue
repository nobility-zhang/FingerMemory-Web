<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';
import { CircleHelp, X } from '@lucide/vue';

const props = defineProps<{
  open: boolean;
}>();

const emit = defineEmits<{
  close: [];
}>();

const dialog = ref<HTMLElement>();
const closeButton = ref<HTMLButtonElement>();
let previousFocus: HTMLElement | null = null;
let previousOverflow = '';

const shortcuts = [
  ['Enter', '提交答案 / 继续'],
  ['1 / 2 / 3 / 4', '评分态选择 Again / Hard / Good / Easy'],
  ['Escape', '关闭弹窗 / 暂停训练'],
  ['Ctrl + H', '显示下一条提示（转练习）'],
  ['Ctrl + Enter', '揭晓答案（转练习）'],
  ['Ctrl + Shift + Enter', '跳过当前目标（转练习）'],
  ['Ctrl + Shift + F', '专注模式'],
  ['?', '打开快捷键帮助'],
] as const;

function restoreFocus(): void {
  if (previousFocus?.isConnected) {
    previousFocus.focus();
  }
  document.body.style.overflow = previousOverflow;
}

function close(): void {
  emit('close');
}

function handleKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    event.preventDefault();
    event.stopPropagation();
    close();
    return;
  }
  if (event.key !== 'Tab' || dialog.value === undefined) {
    return;
  }
  const focusable = [...dialog.value.querySelectorAll<HTMLElement>(
    'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
  )].filter((element) => !element.hasAttribute('disabled'));
  if (focusable.length === 0) {
    event.preventDefault();
    dialog.value.focus();
    return;
  }
  const first = focusable[0];
  const last = focusable.at(-1);
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last?.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first?.focus();
  }
}

watch(
  () => props.open,
  async (open) => {
    if (open) {
      previousFocus = document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null;
      previousOverflow = document.body.style.overflow;
      document.body.style.overflow = 'hidden';
      await Promise.resolve();
      closeButton.value?.focus();
    } else {
      restoreFocus();
    }
  },
);

onBeforeUnmount(() => {
  if (props.open) {
    restoreFocus();
  }
});
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="shortcut-backdrop" @click.self="close">
      <section
        ref="dialog"
        class="shortcut-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="shortcut-title"
        tabindex="-1"
        @keydown="handleKeydown"
      >
        <header>
          <span class="shortcut-icon" aria-hidden="true">
            <CircleHelp :size="22" />
          </span>
          <div>
            <p>操作导航</p>
            <h2 id="shortcut-title">快捷键</h2>
          </div>
          <button
            ref="closeButton"
            type="button"
            class="icon-button"
            aria-label="关闭快捷键帮助"
            @click="close"
          >
            <X :size="20" />
          </button>
        </header>
        <dl>
          <div v-for="[keys, description] in shortcuts" :key="keys">
            <dt><kbd>{{ keys }}</kbd></dt>
            <dd>{{ description }}</dd>
          </div>
        </dl>
        <p class="shortcut-note">
          输入法组合、数字输入、Tab 焦点和浏览器快捷键优先。
        </p>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.shortcut-backdrop {
  position: fixed;
  z-index: 1000;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgb(1 9 20 / 72%);
  backdrop-filter: blur(8px);
}

.shortcut-dialog {
  width: min(520px, 100%);
  max-height: min(720px, calc(100vh - 48px));
  overflow: auto;
  padding: 22px;
  border: 1px solid rgb(85 215 232 / 28%);
  border-radius: 8px;
  background: #0d2035;
  color: #edf8ff;
  box-shadow: 0 28px 80px rgb(0 0 0 / 45%);
}

header {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
}

header p {
  margin: 0 0 2px;
  color: #8ca9c1;
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

h2 {
  margin: 0;
  font-size: 1.35rem;
  letter-spacing: 0;
}

.shortcut-icon {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 8px;
  background: rgb(85 215 232 / 12%);
  color: #55d7e8;
}

.icon-button {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  padding: 0;
  border: 1px solid rgb(158 181 200 / 30%);
  border-radius: 6px;
  background: transparent;
  color: #dcecf7;
  cursor: pointer;
}

dl {
  display: grid;
  gap: 0;
  margin: 20px 0 14px;
}

dl > div {
  display: grid;
  grid-template-columns: minmax(150px, 0.8fr) minmax(0, 1.2fr);
  gap: 14px;
  align-items: center;
  padding: 11px 0;
  border-bottom: 1px solid rgb(158 181 200 / 14%);
}

dt,
dd {
  margin: 0;
}

dd {
  color: #a8c0d2;
  font-size: 0.82rem;
}

kbd {
  display: inline-block;
  padding: 5px 8px;
  border: 1px solid rgb(85 215 232 / 28%);
  border-bottom-color: rgb(85 215 232 / 50%);
  border-radius: 5px;
  background: #122b43;
  color: #dffaff;
  font: 600 0.73rem/1.2 "SFMono-Regular", Consolas, monospace;
}

.shortcut-note {
  margin: 0;
  color: #8ca9c1;
  font-size: 0.76rem;
  line-height: 1.6;
}

button:focus-visible,
.shortcut-dialog:focus-visible {
  outline: 3px solid rgb(85 215 232 / 42%);
  outline-offset: 2px;
}

@media (max-width: 560px) {
  .shortcut-backdrop {
    padding: 12px;
  }

  dl > div {
    grid-template-columns: 1fr;
    gap: 6px;
  }
}
</style>
