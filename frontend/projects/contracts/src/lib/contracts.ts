export interface CreateGameResponse {
  gameId: string;
  joinCode: string;
  joinCodeDisplay: string;
  state: string;
  you: { playerId: string; displayName: string; isHost: boolean };
  sessionToken: string;
  displayToken: string;
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

export interface GameSnapshotDto {
  gameId: string;
  joinCode: string;
  joinCodeDisplay: string;
  state: string;
  players: PlayerDto[];
  currentPerformance: Record<string, unknown> | null;
  ranking: RankingPageDto;
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

export interface RankingUpdatedData {
  entries: RankingEntry[];
  totalPlayers: number;
}

export interface SessionInfo {
  gameId: string;
  token: string;
  displayToken: string;
  isHost: boolean;
  playerId: string;
  displayName: string;
}
