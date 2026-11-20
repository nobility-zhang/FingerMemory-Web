import { createApp } from 'vue';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type {
  AccountClient,
  GmContent,
  GmContentPreview,
} from '../api/client';
import GmContentPanel from './GmContentPanel.vue';

function content(overrides: Partial<GmContent> = {}): GmContent {
  return {
    id: 'content-1',
    namespace: 'fm.demo',
    bookId: 'managed-book',
    contentVersion: '1.0.0',
    contentHash: 'a'.repeat(64),
    title: '管理冒险',
    seriesId: 'managed-series',
    entryNodeId: 'managed.start',
    wordCount: 12,
    chapterCount: 1,
    nodeCount: 5,
    characterCount: 1,
    cardCount: 6,
    encounterCount: 1,
    assetCount: 4,
    status: 'draft',
    issues: [],
    version: 0,
    createdAtMs: 1,
    updatedAtMs: 1,
    ...overrides,
  };
}

function preview(overrides: Partial<GmContentPreview> = {}): GmContentPreview {
  const item = content({
    status: 'validated',
    version: 1,
  });
  return {
    content: item,
    chapters: [{
      chapterId: 'managed-chapter',
      title: '管理内容',
      nodes: [{
        nodeId: 'managed.start',
        chapterId: 'managed-chapter',
        type: 'dialogue',
        title: '入口',
        speaker: '引路人',
        text: '风从驿站门前穿过。',
        portraitCharacterId: 'guide',
        costumeId: 'summer-travel',
        sceneId: 'managed-scene',
        endingId: 'main',
        inheritedFlags: [],
        choices: [{
          choiceId: 'go',
          label: '出发',
          targetNodeId: 'managed.end',
        }],
      }],
    }],
    characters: [{
      characterKey: 'guide',
      name: '引路人',
      epithet: '测试向导',
      description: '用于管理预览。',
      age: 18,
      defaultCostumeId: 'summer-travel',
      costumes: {
        'summer-travel': {
          neutral: '/api/v1/gm/content/content-1/assets/assets/neutral.png',
          welcome: '/api/v1/gm/content/content-1/assets/assets/welcome.png',
        },
      },
      dialogue: {
        welcome: '欢迎来到测试邮站。',
        again: '再检查一次路线。',
        rest: '先休息。',
        training: '跟着路标练习。',
        practice: '这是练习。',
        result: '路线完成。',
      },
    }],
    scenes: [{
      sceneId: 'managed-scene',
      name: '测试邮站',
      background: '/api/v1/gm/content/content-1/assets/assets/scene.png',
      battleTheme: 'bridge',
    }],
    assets: [],
    ...overrides,
  };
}

function buttonByText(root: ParentNode, text: string): HTMLButtonElement {
  return [...root.querySelectorAll<HTMLButtonElement>('button')].find(
    (button) => button.textContent?.includes(text),
  )!;
}

let mountedApp: ReturnType<typeof createApp> | null = null;

afterEach(() => {
  mountedApp?.unmount();
  mountedApp = null;
  document.body.innerHTML = '';
});

describe('GmContentPanel', () => {
  it('runs draft validation, publication and archive through GM actions', async () => {
    let current = content();
    const listGmContent = vi.fn(async () => ({
      items: [current],
      nextCursor: null,
    }));
    const actGmContent = vi.fn(async (
      _contentId: string,
      _key: string,
      input: {
        action: 'validate' | 'publish' | 'archive';
        expectedContentHash: string;
        reason: string;
      },
    ) => {
      current = content({
        status: input.action === 'validate'
          ? 'validated'
          : input.action === 'publish'
            ? 'published'
            : 'archived',
        version: current.version + 1,
      });
      return current;
    });
    const getGmContentPreview = vi.fn(async () => preview({
      content: current,
    }));
    const api = {
      listGmContent,
      actGmContent,
      getGmContentPreview,
      uploadGmContent: vi.fn(),
    } as unknown as AccountClient;
    const root = document.createElement('div');
    document.body.append(root);
    mountedApp = createApp(GmContentPanel, { api });
    mountedApp.mount(root);

    await vi.waitFor(() => {
      expect(root.textContent).toContain('沙盒草稿');
      expect(root.textContent).toContain('管理冒险');
    });

    buttonByText(root, '校验预览').click();
    await vi.waitFor(() => {
      expect(actGmContent).toHaveBeenCalledWith(
        'content-1',
        expect.stringMatching(/.+/),
        expect.objectContaining({
          action: 'validate',
          expectedContentHash: 'a'.repeat(64),
        }),
      );
      expect(root.textContent).toContain('校验通过');
      expect(getGmContentPreview).toHaveBeenCalledWith('content-1');
      expect(root.textContent).toContain('角色、服装与对白');
      expect(root.querySelector<HTMLImageElement>('.preview-portrait')?.src)
        .toContain('/assets/assets/welcome.png');
    });

    buttonByText(root, '发布').click();
    await vi.waitFor(() => {
      expect(root.textContent).toContain('已发布');
    });

    buttonByText(root, '归档').click();
    await vi.waitFor(() => {
      expect(root.textContent).toContain('已归档');
      expect(root.textContent).toContain('现有存档保留');
    });
    expect(listGmContent).toHaveBeenCalledTimes(4);
  });

  it('uploads a new draft without exposing a publish action', async () => {
    const uploadGmContent = vi.fn(async () => content());
    const api = {
      listGmContent: vi.fn(async () => ({ items: [], nextCursor: null })),
      uploadGmContent,
      actGmContent: vi.fn(),
    } as unknown as AccountClient;
    const root = document.createElement('div');
    document.body.append(root);
    mountedApp = createApp(GmContentPanel, { api });
    mountedApp.mount(root);

    await vi.waitFor(() => {
      expect(root.textContent).toContain('冒险内容与不可变版本');
    });
    const input = root.querySelector<HTMLInputElement>('input[type="file"]')!;
    Object.defineProperty(input, 'files', {
      configurable: true,
      value: [new File(['content'], 'managed.fmbook', {
        type: 'application/zip',
      })],
    });
    input.dispatchEvent(new Event('change', { bubbles: true }));
    await vi.waitFor(() => {
      expect(buttonByText(root, '保存沙盒草稿').disabled).toBe(false);
    });
    buttonByText(root, '保存沙盒草稿').click();
    await vi.waitFor(() => {
      expect(uploadGmContent).toHaveBeenCalledWith(
        expect.any(File),
        expect.stringMatching(/.+/),
        expect.any(String),
      );
    });
    expect(root.textContent).not.toContain('发布不可变');
  });
});
