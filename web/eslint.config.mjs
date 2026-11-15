import { withVueTs, vueTsConfigs } from '@vue/eslint-config-typescript';
import pluginVue from 'eslint-plugin-vue';

export default withVueTs(
  {
    ignores: ['coverage/**', 'dist/**', 'node_modules/**'],
  },
  pluginVue.configs['flat/recommended'],
  vueTsConfigs.recommended,
  {
    files: ['**/*.{ts,vue}'],
    rules: {
      curly: ['error', 'all'],
      'vue/html-self-closing': 'off',
      'vue/max-attributes-per-line': [
        'error',
        {
          multiline: 1,
          singleline: 4,
        },
      ],
      'vue/multi-word-component-names': 'off',
      'vue/singleline-html-element-content-newline': 'off',
    },
  },
  {
    files: ['**/*.spec.ts'],
    rules: {
      'vue/one-component-per-file': 'off',
    },
  },
);
