import cheer from '../assets/companions/lia/runtime/cheer.png';
import concern from '../assets/companions/lia/runtime/concern.png';
import neutral from '../assets/companions/lia/runtime/neutral.png';
import relief from '../assets/companions/lia/runtime/relief.png';
import surprise from '../assets/companions/lia/runtime/surprise.png';
import welcome from '../assets/companions/lia/runtime/welcome.png';

export const companionExpressions = [
  'neutral',
  'welcome',
  'cheer',
  'concern',
  'relief',
  'surprise',
] as const;

export type CompanionExpression = (typeof companionExpressions)[number];

export interface CompanionProfile {
  id: string;
  name: string;
  epithet: string;
  portraits: Record<CompanionExpression, string>;
}

export const lia: CompanionProfile = {
  id: 'lia',
  name: '莉娅',
  epithet: '暖灯旅店的看板娘',
  portraits: {
    neutral,
    welcome,
    cheer,
    concern,
    relief,
    surprise,
  },
};
