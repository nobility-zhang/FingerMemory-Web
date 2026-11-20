import { createApp, nextTick } from 'vue';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type {
  AccountClient,
  ContentPackageInstallation,
  ContentPackagePreview,
} from '../api/client';
import ContentLibraryPanel from './ContentLibraryPanel.vue';

const preview: ContentPackagePreview = {
  fileName: 'warm-lantern.fmbook',
  title: '暖灯与失语之森',
  namespace: 'fm.demo',
  bookId: 'warm-lantern',
  contentVersion: '1.0.0',
  contentHash: 'a'.repeat(64),
  engineVersion: '0.3.0',
  entryNodeId: 'inn',
  seriesId: 'warm-lantern',
  requirements: [],
  wordCount: 6,
  chapterCount: 1,
  nodeCount: 4,
  characterCount: 2,
  cardCount: 7,
  encounterCount: 1,
  assetCount: 3,
  warnings: [],
};

const installation: ContentPackageInstallation = {
  id: 'installation-1',
  namespace: preview.namespace,
  bookId: preview.bookId,
  contentVersion: preview.contentVersion,
  contentHash: preview.contentHash,
  title: preview.title,
  entryNodeId: preview.entryNodeId,
  seriesId: preview.seriesId,
  requirements: preview.requirements,
  status: 'installed',
  wordCount: preview.wordCount,
  chapterCount: preview.chapterCount,
  nodeCount: preview.nodeCount,
  characterCount: preview.characterCount,
  cardCount: preview.cardCount,
  encounterCount: preview.encounterCount,
  assetCount: preview.assetCount,
  installedAtMs: Date.parse('2026-11-18T10:00:00Z'),
};

let mountedApp: ReturnType<typeof createApp> | null = null;

afterEach(() => {
  mountedApp?.unmount();
  mountedApp = null;
  document.body.innerHTML = '';
});

function mountPanel(overrides: Partial<AccountClient> = {}) {
  const api = {
    listContentPackages: vi.fn(async () => []),
    listContentCatalog: vi.fn(async () => []),
    installCatalogContent: vi.fn(),
    previewContentPackage: vi.fn(async () => preview),
    installContentPackage: vi.fn(async () => ({
      outcome: 'installed' as const,
      installation,
    })),
    ...overrides,
  } as unknown as AccountClient;
  const root = document.createElement('div');
  document.body.append(root);
  mountedApp = createApp(ContentLibraryPanel, { api });
  mountedApp.mount(root);
  return { root, api };
}

function selectPackage(root: HTMLElement): void {
  const input = root.querySelector<HTMLInputElement>('input[type="file"]')!;
  const file = new File(['archive'], 'warm-lantern.fmbook', {
    type: 'application/zip',
  });
  Object.defineProperty(input, 'files', {
    configurable: true,
    value: [file],
  });
  input.dispatchEvent(new Event('change', { bubbles: true }));
}

describe('ContentLibraryPanel', () => {
  it('previews and installs a validated package before refreshing the list', async () => {
    const listContentPackages = vi.fn()
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([installation]);
    const { root, api } = mountPanel({ listContentPackages });

    await vi.waitFor(() => {
      expect(root.textContent).toContain('当前账号还没有安装冒险内容包');
    });
    selectPackage(root);
    await vi.waitFor(() => {
      expect(api.previewContentPackage).toHaveBeenCalledOnce();
      expect(root.textContent).toContain('安装预览');
      expect(root.textContent).toContain('暖灯与失语之森');
      expect(root.textContent).toContain('词条');
    });

    root.querySelector<HTMLButtonElement>('.preview-actions .primary-action')!.click();
    await vi.waitFor(() => {
      expect(api.installContentPackage).toHaveBeenCalledWith(
        expect.any(File),
        expect.stringMatching(/.+/),
      );
      expect(listContentPackages).toHaveBeenCalledTimes(2);
      expect(root.textContent).toContain('已安装');
      expect(root.textContent).toContain('v1.0.0');
    });
    await nextTick();
  });

  it('shows the server validation error and keeps no install action', async () => {
    const { root } = mountPanel({
      previewContentPackage: vi.fn(async () => {
        throw new Error('内容包包含路径越界');
      }),
    });
    await vi.waitFor(() => {
      expect(root.textContent).toContain('当前账号还没有安装冒险内容包');
    });
    selectPackage(root);
    await vi.waitFor(() => {
      expect(root.querySelector('[role="alert"]')?.textContent)
        .toContain('路径越界');
      expect(root.textContent).not.toContain('安装预览');
    });
  });

  it('installs a GM-published shared version from the catalog', async () => {
    const catalogEntry = {
      id: 'published-content-1',
      title: '共享冒险',
      namespace: 'fm.demo',
      bookId: 'shared-book',
      contentVersion: '1.0.0',
      contentHash: 'd'.repeat(64),
      seriesId: 'shared-book',
      entryNodeId: 'shared.start',
      wordCount: 10,
      chapterCount: 1,
      nodeCount: 5,
      characterCount: 1,
      cardCount: 6,
      encounterCount: 1,
      assetCount: 4,
      publishedAtMs: 2,
    };
    const publishedInstallation: ContentPackageInstallation = {
      ...installation,
      id: 'installation-shared',
      catalogContentId: catalogEntry.id,
      title: catalogEntry.title,
      namespace: catalogEntry.namespace,
      bookId: catalogEntry.bookId,
      contentVersion: catalogEntry.contentVersion,
      contentHash: catalogEntry.contentHash,
      wordCount: catalogEntry.wordCount,
      chapterCount: catalogEntry.chapterCount,
      nodeCount: catalogEntry.nodeCount,
      characterCount: catalogEntry.characterCount,
      cardCount: catalogEntry.cardCount,
      encounterCount: catalogEntry.encounterCount,
      assetCount: catalogEntry.assetCount,
    };
    const listContentPackages = vi.fn()
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([publishedInstallation]);
    const installCatalogContent = vi.fn(async () => ({
      outcome: 'installed' as const,
      installation: publishedInstallation,
    }));
    const { root, api } = mountPanel({
      listContentPackages,
      listContentCatalog: vi.fn(async () => [catalogEntry]),
      installCatalogContent,
    });

    await vi.waitFor(() => {
      expect(root.textContent).toContain('共享冒险书架');
      expect(root.textContent).toContain('共享冒险');
    });
    const installButton = [...root.querySelectorAll<HTMLButtonElement>('button')]
      .find((button) => button.textContent?.includes('安装共享版本'));
    installButton?.click();
    await vi.waitFor(() => {
      expect(installCatalogContent).toHaveBeenCalledWith(
        catalogEntry.id,
        expect.stringMatching(/.+/),
      );
      expect(root.textContent).toContain('已安装');
    });
    expect(api.listContentPackages).toHaveBeenCalledTimes(2);
  });
});
