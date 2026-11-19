export interface UserPreferencesView {
  nickname: string;
  avatarKey: string;
  timezone: string;
  dailyGoal: number;
  theme: string;
  soundEnabled: boolean;
  shortcutsJson: string;
}

export interface UserView {
  id: string;
  username: string;
  displayName: string;
  mustChangePassword?: boolean;
  preferences: UserPreferencesView;
}

export interface LexiconView {
  id: string;
  name: string;
  description: string;
  source: string;
  archivedAtMs: number | null;
  createdAtMs: number;
  updatedAtMs: number;
  version: number;
}

export interface WordEntryView {
  id: string;
  term: string;
  meaning: string;
  acceptedAnswers: string[];
  hints: string[];
  normalizedTerm: string;
  createdAt: number;
  updatedAt: number;
}

export interface StudyItemView {
  id: string;
  wordEntryId: string;
  direction: 'meaning-to-spelling';
  createdAt: number;
  updatedAt: number;
}

export interface LexiconItemView {
  lexiconId: string;
  studyItemId: string;
  order: number;
  sourceHints: string[];
}

export interface SessionItemView {
  wordEntry: WordEntryView;
  studyItem: StudyItemView;
  lexiconItem: LexiconItemView | null;
  reviewState?: unknown;
  reviewVersion: number;
}

export interface SessionQueueItemView {
  studyItemId: string;
  wordEntryId: string;
  status: 'pending' | 'completed' | 'skipped';
  source: 'new' | 'review' | 'retry';
}

export interface PendingAnswerView {
  studyItemId: string;
  response: string;
  answerWasHidden: boolean;
  hintUsed: boolean;
  answerRevealed: boolean;
  inputMatchedAnswer: boolean;
  assessmentType?: 'typed-recall' | 'flashcard-self-report' | 'guided-practice';
  assessmentPolicyVersion?: 1 | 2;
  assessmentStage?: 'prompt' | 'attempt' | 'reveal-for-check' | 'rate';
  priorExposure?: boolean;
  explicitNotRemembered?: boolean;
  practiceAttemptId: string;
  reviewSubmissionId: string;
  submittedAt: number;
}

export interface PracticeSessionView {
  id: string;
  modeId: string;
  status: 'active' | 'paused' | 'completed';
  queue: SessionQueueItemView[];
  currentIndex: number;
  currentStudyItemId: string | null;
  interactions: Record<string, unknown>;
  pendingAnswer: PendingAnswerView | null;
  speed: number;
  reduceMotion: boolean;
  createdAt: number;
  updatedAt: number;
  pausedAt: number | null;
  completedAt: number | null;
  endedReason?: 'completed' | 'abandoned';
  version: number;
}

export interface SessionEnvelope {
  session: PracticeSessionView;
  items: SessionItemView[];
  dataVersion: number;
}

export interface SessionListResponse {
  sessions: SessionEnvelope[];
  dataVersion: number;
}

export interface QueueEntryView {
  studyItem: StudyItemView;
  wordEntry: WordEntryView;
  reviewState?: unknown;
  source: 'new' | 'review';
}

export interface QueuePlanView {
  at: number;
  timeZone: string;
  dayStartAt: number;
  dayEndAt: number;
  settings: {
    newLimit: number;
    reviewLimit: number;
    paused: boolean;
  };
  dueAvailableCount: number;
  newAvailableCount: number;
  selected: QueueEntryView[];
  dataVersion: number;
}

export interface ReviewPreviewView {
  previews: Record<
    'again' | 'hard' | 'good' | 'easy',
    { rating: string; dueAt: number; scheduledDays: number }
  >;
  schedulerPackageVersion: string;
  schedulerModelVersion: string;
  schedulerConfigHash: string;
  reviewedAtMs: number;
  reviewVersion: number;
  sessionVersion: number;
  dataVersion: number;
}

export interface PracticeResponseResult {
  attempt: unknown;
  pendingAnswer: PendingAnswerView;
  session: PracticeSessionView;
  dataVersion: number;
}

export interface PracticeCompletionResult {
  attempt: {
    id: string;
    inputMatchedAnswer: boolean;
    hintUsed: boolean;
    answerRevealed: boolean;
    abandoned: boolean;
  };
  session: PracticeSessionView;
  dataVersion: number;
}

export interface ReviewSubmissionResult {
  outcome: 'created' | 'migrated';
  state: { dueAt: number; version: number };
  log: { submissionId: string; reviewedAt: number };
  session: PracticeSessionView | null;
  dataVersion: number;
  rewardEligible: boolean;
}

export interface BattleCardInstanceView {
  instanceId: string;
  definitionId:
    | 'traveler-strike'
    | 'companion-guard'
    | 'ember-rune'
    | 'herb-pouch'
    | 'observe-signpost'
    | 'party-strike';
}

export interface BattleEnemyView {
  id: string;
  hp: number;
  maxHp: number;
  block: number;
  burn: {
    remainingTurns: number;
    damagePerTurn: number;
  } | null;
}

export interface BattleStateView {
  schemaVersion: number;
  battleRulesVersion: 'card-battle-v1';
  battleId: string;
  encounterId: string;
  turnId: number;
  phase: 'learning' | 'player-action' | 'victory' | 'defeat';
  stateVersion: number;
  rngState: number;
  party: {
    hp: number;
    maxHp: number;
    block: number;
  };
  enemies: BattleEnemyView[];
  energy: number;
  drawPile: BattleCardInstanceView[];
  drawCursor: number;
  hand: BattleCardInstanceView[];
  discardPile: BattleCardInstanceView[];
  exhaustPile: BattleCardInstanceView[];
  currentIntent: {
    enemyId: string;
    type: 'attack' | 'defend';
    value: number;
  };
  intentCycle: Array<{
    type: 'attack' | 'defend';
    value: number;
  }>;
  intentIndex: number;
  terminal: 'victory' | 'defeat' | null;
  learningTask: {
    taskId: string;
    sessionId: string;
    studyItemId: string;
    energyGranted: boolean;
    submissionId: string | null;
  } | null;
  companion: {
    key: string;
    skillName?: string;
    skillDescription?: string;
    skillType?: 'heal' | 'block' | 'draw' | 'damage';
    skillAmount?: number;
    skillUsed: boolean;
  } | null;
}

export interface BattleEnvelopeView {
  battle: BattleStateView;
  events: Array<Record<string, unknown>>;
  stateVersion: number;
}

export interface AdventureChoiceView {
  choiceId: string;
  label: string;
}

export interface AdventureNodeView {
  nodeId: string;
  chapterId: string;
  type: 'dialogue' | 'choice' | 'explore' | 'encounter' | 'camp' | 'chapter-end';
  title: string;
  speaker: string;
  text: string;
  encounterId?: string;
  portraitCharacterId?: string;
  sceneId?: string;
  costumeId?: string;
  encounterName?: string;
  encounterDescription?: string;
  choices: AdventureChoiceView[];
}

export interface AdventureCharacterView {
  characterKey: string;
  installationId: string;
  name: string;
  epithet: string;
  description: string;
  age: number;
  defaultCostumeId: string;
  costumes: Record<string, Record<string, string>>;
  dialogue: {
    welcome: string;
    again: string;
    rest: string;
    training: string;
    practice: string;
    result: string;
  };
  skill: {
    name: string;
    description: string;
    type: 'heal' | 'block' | 'draw' | 'damage';
    amount: number;
  } | null;
}

export interface AdventureSceneView {
  sceneId: string;
  name: string;
  background: string;
  battleTheme: string;
  ambientKey?: string;
}

export interface AdventureRewardChoiceView {
  choiceId: string;
  cardDefinitionId:
    | 'traveler-strike'
    | 'companion-guard'
    | 'ember-rune'
    | 'herb-pouch'
    | 'observe-signpost'
    | 'party-strike';
  name: string;
  description: string;
}

export interface AdventureRewardOfferView {
  offerId: string;
  nodeId: string;
  choices: AdventureRewardChoiceView[];
}

export interface AdventureRunView {
  id: string;
  installationId: string;
  namespace: string;
  bookId: string;
  contentVersion: string;
  contentHash: string;
  title: string;
  seriesId: string;
  companionKey: string;
  status: 'active' | 'completed';
  runVersion: number;
  sessionId: string;
  battleId?: string;
  currentNode: AdventureNodeView;
  deck: Array<AdventureRewardChoiceView['cardDefinitionId']>;
  unlockedCharacters: string[];
  characters?: AdventureCharacterView[];
  scenes?: AdventureSceneView[];
  sourceCompletionId?: string;
  rewardOffer?: AdventureRewardOfferView;
  flags: Record<string, boolean>;
  createdAtMs: number;
  updatedAtMs: number;
}

export interface AdventureStartResult {
  outcome: 'created' | 'existing';
  run: AdventureRunView;
}

export interface AdventureUpgradeResult {
  outcome: 'upgraded';
  run: AdventureRunView;
}

export interface AdventureActionResult {
  outcome: 'applied';
  run: AdventureRunView;
}

export interface AdventureEncounterView {
  outcome: 'started' | 'existing';
  battleId: string;
  runId: string;
  runVersion: number;
}

export interface AdventureRecollectionView {
  key: string;
  namespace: string;
  bookId: string;
  chapterId: string;
  title: string;
  speaker: string;
  text: string;
  unlockedAtMs: number;
}

export interface AdventureCompletionView {
  id: string;
  runId: string;
  namespace: string;
  bookId: string;
  seriesId: string;
  contentVersion: string;
  contentHash: string;
  endingId: string;
  endingTitle: string;
  endingText: string;
  inheritedFlags: Record<string, boolean>;
  completedAtMs: number;
}

export interface MigrationPreview {
  snapshotFingerprint: string;
  targetDataVersion: number;
  counts: Record<string, number>;
  schedulerPackageVersion: string;
  schedulerModelVersion: string;
  schedulerWeights: number;
}

export interface MigrationResult {
  batchId: string;
  snapshotFingerprint: string;
  dataVersion: number;
  importedCounts: Record<string, number>;
  idMappings: number;
}

export interface AccountBackup {
  backupVersion: number;
  schemaVersion: number;
  dataFormatVersion: number;
  exportedAt: number;
  tables: Record<string, unknown>;
  [key: string]: unknown;
}

export interface BackupPreview {
  fingerprint: string;
  exportedAt: number;
  sourceDataVersion: number;
  counts: Record<string, number>;
  reviewReceipts: number;
  rewardEvents: number;
  migrationReceipts: number;
  idMappings: number;
}

export interface BackupRestoreResult {
  outcome: 'restored';
  fingerprint: string;
  dataVersion: number;
  counts: Record<string, number>;
}

export interface GmContext {
  role: 'ADMIN';
  capabilities: Array<
    'manage-users' | 'manage-content' | 'manage-sandboxes' | 'read-audit'
  >;
}

export interface GmUser {
  id: string;
  username: string;
  displayName: string;
  role: 'USER' | 'ADMIN';
  status: 'active' | 'disabled';
  accountKind: 'standard' | 'test';
  version: number;
}

export interface GmUserPage {
  items: GmUser[];
  nextCursor: string | null;
}

export interface GmCreateUserInput {
  username: string;
  displayName: string;
  password: string;
  role: 'USER' | 'ADMIN';
  accountKind: 'standard' | 'test';
  reason: string;
}

export interface GmUpdateUserInput {
  expectedVersion: number;
  reason: string;
  role?: 'USER' | 'ADMIN';
  status?: 'active' | 'disabled';
}

export interface GmAuditEvent {
  id: string;
  actorId: string;
  action: string;
  target: string;
  scope: string;
  occurredAtMs: number;
  requestId: string;
  reason: string;
  outcome: 'success' | 'rejected' | 'failed';
}

export interface GmAuditPage {
  items: GmAuditEvent[];
  nextCursor: string | null;
}

export interface GmSandboxState {
  schemaVersion: number;
  fixtureVersion: number;
  fixtureKey: string;
  currentNodeId: string;
  encounterId: string | null;
  clockMode: 'fixed' | 'real';
  virtualNowMs: number | null;
  randomSeed: number;
  deck: string[];
  unlockedCharacters: string[];
  availableNodes: string[];
  availableCards: string[];
  availableCharacters: string[];
}

export interface GmSandbox {
  id: string;
  scope: 'sandbox';
  ownerAdminId: string;
  templateId: string;
  contentInstallationId: string | null;
  version: number;
  dataEpoch: string;
  state: GmSandboxState;
}

export interface GmSandboxPage {
  items: GmSandbox[];
  nextCursor: string | null;
}

export interface GmCreateSandboxInput {
  templateId: string;
  contentInstallationId?: string | null;
  reason: string;
}

type GmSandboxCommandPayload =
  | { command: 'jump-node'; args: { nodeId: string } }
  | { command: 'grant-cards'; args: { cardDefinitionIds: string[] } }
  | { command: 'unlock-character'; args: { characterKey: string } }
  | {
      command: 'set-clock';
      args:
        | { mode: 'fixed'; nowMs: number }
        | { mode: 'real'; nowMs?: never };
    }
  | { command: 'reset-sandbox'; args: Record<string, never> };

export type GmSandboxCommandInput = GmSandboxCommandPayload & {
  actionId: string;
  expectedVersion: number;
  dataEpoch: string;
  reason: string;
};

export interface GmCommandReceipt {
  commandId: string;
  status: 'applied' | 'duplicate';
  scope: 'sandbox';
  sandbox: GmSandbox;
}

export interface ContentPackagePreview {
  fileName: string;
  title: string;
  namespace: string;
  bookId: string;
  contentVersion: string;
  contentHash: string;
  engineVersion: string;
  entryNodeId: string;
  seriesId: string;
  requirements: ContentPackageRequirement[];
  wordCount: number;
  chapterCount: number;
  nodeCount: number;
  characterCount: number;
  cardCount: number;
  encounterCount: number;
  assetCount: number;
  warnings: string[];
}

export interface ContentPackageInstallation {
  id: string;
  namespace: string;
  bookId: string;
  contentVersion: string;
  contentHash: string;
  title: string;
  entryNodeId: string;
  seriesId: string;
  requirements: ContentPackageRequirement[];
  status: 'installed';
  wordCount: number;
  chapterCount: number;
  nodeCount: number;
  characterCount: number;
  cardCount: number;
  encounterCount: number;
  assetCount: number;
  installedAtMs: number;
}

export interface ContentPackageRequirement {
  bookId: string;
  endingId: string;
  endingTitle: string;
  minContentVersion?: string;
  maxContentVersionExclusive?: string;
}

export interface ContentPackageInstallResult {
  outcome: 'installed' | 'duplicate';
  installation: ContentPackageInstallation;
}

export interface AccountClient {
  loadCurrentUser(): Promise<UserView | null>;
  login(username: string, password: string): Promise<UserView>;
  register(
    username: string,
    password: string,
    displayName: string,
  ): Promise<UserView>;
  changePassword(
    currentPassword: string,
    newPassword: string,
  ): Promise<void>;
  logout(): Promise<void>;
  listLexicons(): Promise<LexiconView[]>;
  createLexicon(name: string, description: string): Promise<LexiconView>;
  loadQueuePlan(input: {
    timeZone: string;
    newLimit?: number;
    reviewLimit?: number;
  }): Promise<QueuePlanView>;
  listActiveSessions(): Promise<SessionListResponse>;
  createSession(input: {
    modeId: string;
    timeZone: string;
    newLimit?: number;
    reviewLimit?: number;
    reduceMotion: boolean;
  }): Promise<SessionEnvelope>;
  getSession(sessionId: string): Promise<SessionEnvelope>;
  updateSession(input: {
    sessionId: string;
    expectedDataVersion: number;
    expectedVersion: number;
    status: 'active' | 'paused' | 'completed';
  }): Promise<SessionEnvelope>;
  submitPracticeResponse(input: {
    sessionId: string;
    practiceAttemptId: string;
    reviewSubmissionId: string;
    studyItemId: string;
    response: string;
    assessmentType?: 'typed-recall' | 'flashcard-self-report' | 'guided-practice';
    assessmentPolicyVersion?: 1 | 2;
    assessmentStage?: 'prompt' | 'attempt' | 'reveal-for-check' | 'rate';
    priorExposure?: boolean;
    explicitNotRemembered?: boolean;
    answerWasHidden?: boolean;
    hintUsed?: boolean;
    answerRevealed?: boolean;
    abandoned?: boolean;
    expectedDataVersion: number;
    expectedSessionVersion: number;
  }): Promise<PracticeResponseResult>;
  submitPracticeCompletion(input: {
    sessionId: string;
    practiceAttemptId: string;
    studyItemId: string;
    response: string;
    assessmentType?: 'typed-recall' | 'flashcard-self-report' | 'guided-practice';
    assessmentPolicyVersion?: 1 | 2;
    assessmentStage?: 'prompt' | 'attempt' | 'reveal-for-check' | 'rate';
    priorExposure?: boolean;
    explicitNotRemembered?: boolean;
    answerWasHidden: boolean;
    hintUsed: boolean;
    answerRevealed: boolean;
    abandoned: boolean;
    outcome: 'completed' | 'skipped';
    expectedDataVersion: number;
    expectedSessionVersion: number;
  }): Promise<PracticeCompletionResult>;
  previewReview(input: {
    sessionId: string;
    studyItemId: string;
    expectedDataVersion: number;
    expectedSessionVersion: number;
    expectedReviewVersion: number;
  }): Promise<ReviewPreviewView>;
  submitReview(input: {
    sessionId: string;
    submissionId: string;
    rating: 'again' | 'hard' | 'good' | 'easy';
    assessmentType?: 'typed-recall' | 'flashcard-self-report' | 'guided-practice';
    assessmentPolicyVersion?: 1 | 2;
    assessmentStage?: 'prompt' | 'attempt' | 'reveal-for-check' | 'rate';
    priorExposure?: boolean;
    explicitNotRemembered?: boolean;
    answerWasHidden?: boolean;
    hintUsed?: boolean;
    answerRevealed?: boolean;
    abandoned?: boolean;
    expectedDataVersion: number;
    expectedReviewVersion: number;
    expectedSessionVersion: number;
  }): Promise<ReviewSubmissionResult>;
  createBattle(input: {
    battleId: string;
    encounterId: string;
    sessionId: string;
    studyItemId: string;
    companionKey: string;
    seed?: number;
    shuffle?: boolean;
    actionId: string;
  }): Promise<BattleEnvelopeView>;
  getBattle(battleId: string): Promise<BattleEnvelopeView>;
  claimBattleLearning(input: {
    battleId: string;
    actionId: string;
    expectedStateVersion: number;
    submissionId: string;
  }): Promise<BattleEnvelopeView>;
  playBattleCard(input: {
    battleId: string;
    actionId: string;
    expectedStateVersion: number;
    cardInstanceId: string;
    targetId?: string;
  }): Promise<BattleEnvelopeView>;
  useBattleCompanionSkill(input: {
    battleId: string;
    actionId: string;
    expectedStateVersion: number;
  }): Promise<BattleEnvelopeView>;
  endBattleTurn(input: {
    battleId: string;
    actionId: string;
    expectedStateVersion: number;
    nextSessionId?: string;
    nextStudyItemId?: string;
  }): Promise<BattleEnvelopeView>;
  listAdventures(limit?: number): Promise<AdventureRunView[]>;
  startAdventure(input: {
    installationId: string;
    companionKey: string;
    sourceCompletionId?: string;
    reduceMotion: boolean;
    actionId: string;
  }): Promise<AdventureStartResult>;
  getAdventure(runId: string): Promise<AdventureRunView>;
  upgradeAdventure(
    runId: string,
    idempotencyKey: string,
    installationId: string,
  ): Promise<AdventureUpgradeResult>;
  listUnlockedCompanions(): Promise<string[]>;
  listCompanionDefinitions(): Promise<AdventureCharacterView[]>;
  listAdventureRecollections(
    limit?: number,
  ): Promise<AdventureRecollectionView[]>;
  listAdventureCompletions(
    limit?: number,
  ): Promise<AdventureCompletionView[]>;
  startAdventureEncounter(
    runId: string,
    actionId: string,
  ): Promise<AdventureEncounterView>;
  actAdventure(
    runId: string,
    idempotencyKey: string,
    input: {
      expectedVersion: number;
      action:
        | 'advance'
        | 'choose'
        | 'resolve-encounter'
        | 'claim-reward'
        | 'select-companion'
        | 'complete';
      choiceId?: string;
      battleId?: string;
      companionKey?: string;
    },
  ): Promise<AdventureActionResult>;
  previewContentPackage(file: File): Promise<ContentPackagePreview>;
  installContentPackage(
    file: File,
    idempotencyKey: string,
  ): Promise<ContentPackageInstallResult>;
  listContentPackages(limit?: number): Promise<ContentPackageInstallation[]>;
  previewLocalMigration(snapshot: unknown): Promise<MigrationPreview>;
  importLocalMigration(
    batchId: string,
    snapshot: unknown,
  ): Promise<MigrationResult>;
  exportAccountBackup(): Promise<AccountBackup>;
  previewAccountBackup(backup: AccountBackup): Promise<BackupPreview>;
  restoreAccountBackup(
    idempotencyKey: string,
    backup: AccountBackup,
  ): Promise<BackupRestoreResult>;
  loadGmContext(): Promise<GmContext>;
  listGmUsers(limit?: number, cursor?: string): Promise<GmUserPage>;
  createGmUser(
    idempotencyKey: string,
    input: GmCreateUserInput,
  ): Promise<GmUser>;
  updateGmUser(
    userId: string,
    idempotencyKey: string,
    input: GmUpdateUserInput,
  ): Promise<GmUser>;
  listGmAudit(limit?: number): Promise<GmAuditPage>;
  listGmSandboxes(limit?: number, cursor?: string): Promise<GmSandboxPage>;
  createGmSandbox(
    idempotencyKey: string,
    input: GmCreateSandboxInput,
  ): Promise<GmSandbox>;
  executeGmSandboxCommand(
    sandboxId: string,
    idempotencyKey: string,
    input: GmSandboxCommandInput,
  ): Promise<GmCommandReceipt>;
}
