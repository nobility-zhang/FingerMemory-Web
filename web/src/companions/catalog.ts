import cheer from '../assets/companions/lia/runtime/cheer.png';
import concern from '../assets/companions/lia/runtime/concern.png';
import neutral from '../assets/companions/lia/runtime/neutral.png';
import relief from '../assets/companions/lia/runtime/relief.png';
import surprise from '../assets/companions/lia/runtime/surprise.png';
import welcome from '../assets/companions/lia/runtime/welcome.png';
import serinCheer from '../assets/companions/serin/runtime/cheer.png';
import serinConcern from '../assets/companions/serin/runtime/concern.png';
import serinNeutral from '../assets/companions/serin/runtime/neutral.png';
import serinRelief from '../assets/companions/serin/runtime/relief.png';
import serinSurprise from '../assets/companions/serin/runtime/surprise.png';
import serinWelcome from '../assets/companions/serin/runtime/welcome.png';
import finaCheer from '../assets/companions/fina/runtime/cheer.png';
import finaConcern from '../assets/companions/fina/runtime/concern.png';
import finaNeutral from '../assets/companions/fina/runtime/neutral.png';
import finaRelief from '../assets/companions/fina/runtime/relief.png';
import finaSurprise from '../assets/companions/fina/runtime/surprise.png';
import finaWelcome from '../assets/companions/fina/runtime/welcome.png';

export const companionExpressions = [
  'neutral',
  'welcome',
  'cheer',
  'concern',
  'relief',
  'surprise',
] as const;

export type CompanionExpression = (typeof companionExpressions)[number];

export const companionIds = ['lia', 'serin', 'fina'] as const;

export type CompanionId = (typeof companionIds)[number];

export interface CompanionDialogue {
  welcome: string;
  again: string;
  rest: string;
  training: string;
  practice: string;
  result: string;
}

export interface CompanionProfile {
  id: CompanionId;
  name: string;
  epithet: string;
  portraits: Record<CompanionExpression, string>;
  dialogue: CompanionDialogue;
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
  dialogue: {
    welcome: '热茶刚好。今天的旅途笔记里还有几枚词，我会陪你一项项收好。',
    again: '没关系，这一页只是还不熟悉。我们把灯留在这里，之后再一起遇见它。',
    rest: '先休息一下吧。书签我会放好，等你回来时，灯还亮着。',
    training: '只输入英文拼写就好。Enter 提交，答题时数字键会正常进入答案。',
    practice: '看过提示或答案也没关系，这段会诚实记作练习，不会打乱你的正式笔记。',
    result: '这些已经收好了。接下来的路，我陪你慢慢走。',
  },
};

export const serin: CompanionProfile = {
  id: 'serin',
  name: '瑟琳',
  epithet: '古树契约的旅行祭司',
  portraits: {
    neutral: serinNeutral,
    welcome: serinWelcome,
    cheer: serinCheer,
    concern: serinConcern,
    relief: serinRelief,
    surprise: serinSurprise,
  },
  dialogue: {
    welcome: '先看清楚，再往前走。今天也不用赶时间，我会替你记下每一个路标。',
    again: '这一枚符文还不熟悉。我们记下它，之后再一起遇见。',
    rest: '先把书页合上吧。真正的旅途也包括停下来喘一口气。',
    training: '不用赶时间。先在心里读一遍释义，再写下你记得的英文。',
    practice: '这次已经看见了答案，就把它当作熟悉路标，而不是正式回想。',
    result: '路标重新亮起来了。你刚才记下的东西，已经让下一页有了方向。',
  },
};

export const fina: CompanionProfile = {
  id: 'fina',
  name: '菲妮',
  epithet: '罗盘与远路的绘图师',
  portraits: {
    neutral: finaNeutral,
    welcome: finaWelcome,
    cheer: finaCheer,
    concern: finaConcern,
    relief: finaRelief,
    surprise: finaSurprise,
  },
  dialogue: {
    welcome: '地图给我看看。迷路也没关系，我会先找到最近的水源，再把你带回来。',
    again: '这个路标刚才骗了我们。先记下来，下次换条更清楚的路。',
    rest: '地图不会跑掉。先坐一会儿，我盯着篝火。',
    training: '别急着猜。先把释义读成画面，再写下你记得的英文。',
    practice: '看过答案就算熟悉路线，不算闭着眼走完。',
    result: '路找对了。把这一页收好，下一段我来带路。',
  },
};

export const companions: readonly CompanionProfile[] = [lia, serin, fina];

export const companionById: Record<CompanionId, CompanionProfile> = {
  lia,
  serin,
  fina,
};
