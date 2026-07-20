export interface CreateGameResponse {
  gameId: string;
  joinCode: string;
  joinCodeDisplay: string;
  state: string;
  you: { playerId: string; displayName: string; isHost: boolean };
  sessionToken: string;
  displayToken: string | null;
}

export interface PlayerDto {
  playerId: string;
  displayName: string;
  pictureUrl: string | null;
  score: number;
  isHost: boolean;
  onBreak: boolean;
}

export interface RankingEntry {
  rank: number;
  playerId: string;
  displayName: string;
  score: number;
}

export interface RankingPageDto {
  page: number;
  pageSize: number;
  totalPlayers: number;
  entries: RankingEntry[];
}

export interface PerformanceSlotDto {
  slotId: string;
  slotIndex: number;
  currentPlayerId: string;
  currentPlayerName: string;
  state: 'PENDING' | 'CONFIRMED' | 'REPLACED' | 'VACATED';
}

export interface CurrentPerformanceDto {
  performanceId: number;
  type: string;
  state: 'QUEUED' | 'ANNOUNCED' | 'CONFIRMING' | 'RUNNING' | 'LOCKED' | 'SKIPPED';
  youtubeUrl: string | null;
  confirmDeadlineAt: string | null;
  replacementOpensAt: string | null;
  slots: PerformanceSlotDto[];
  judgePlayerIds: string[];
  durationSeconds: number | null;
  judgingDeadlineAt: string | null;
  startedAt: string | null;
}

export interface PerformanceAnnouncedData {
  performanceId: number;
  type: string;
  slots: PerformanceSlotDto[];
  judgeNames: string[];
  judgePlayerIds: string[];
  youtubeUrl: string | null;
  confirmDeadlineAt: string;
}

export interface SlotStateChangedData {
  performanceId: number;
  slotId: string;
  slotState: string;
  currentPlayerName: string;
}

export interface PerformanceStartedData {
  performanceId: number;
}

export interface PerformanceLockedData {
  performanceId: number;
  scores: ScoreResultDto[];
}

export interface PerformanceSkippedData {
  performanceId: number;
}

export interface ScoreResultDto {
  playerId: string;
  displayName: string;
  points: number;
}

export interface QueuePerformanceRequest {
  type: string;
  youtubeUrl: string;
  performerPlayerIds: string[];
  slotCount: number;
}

export interface EvaluateRequest {
  baseline: CriterionScore[];
  perPerformer: Record<string, CriterionScore[]>;
}

export interface CriterionScore {
  criterion: string;
  score: number;
}

export interface RateRequest {
  overallScore: number;
}

export interface GameSnapshotDto {
  gameId: string;
  joinCode: string;
  joinCodeDisplay: string;
  state: string;
  players: PlayerDto[];
  currentPerformance: CurrentPerformanceDto | null;
  ranking: RankingPageDto;
  queueNonEmpty: boolean;
}

export interface GameEventEnvelope<T = unknown> {
  seq: number;
  type: string;
  at: string;
  data: T;
}

export interface PlayerJoinedData {
  playerId: string;
  displayName: string;
}

export interface GameStartedData {
  gameId: string;
}

export interface GameEndedData {
  gameId: string;
}

export interface RankingUpdatedData {
  entries: RankingEntry[];
  totalPlayers: number;
}

export interface CommentDto {
  commentId: string;
  authorPlayerId: string;
  authorName: string;
  body: string;
  createdAt: string;
  likeCount: number;
  likedByMe: boolean;
}

export interface CommentPostedData {
  commentId: string;
  authorPlayerId: string;
  authorName: string;
  body: string;
  createdAt: string;
}

export interface CommentLikedData {
  commentId: string;
  likeCount: number;
}

export interface SessionInfo {
  gameId: string;
  token: string;
  displayToken: string | null;
  isHost: boolean;
  playerId: string;
  displayName: string;
}
