import { defineConfig } from 'vite';

export default defineConfig({
  build: {
    target: 'node24',
    outDir: 'dist-worker',
    emptyOutDir: true,
    ssr: 'worker/server.ts',
    rollupOptions: {
      output: {
        entryFileNames: 'server.mjs',
        codeSplitting: false,
      },
    },
  },
  ssr: {
    noExternal: true,
  },
});
