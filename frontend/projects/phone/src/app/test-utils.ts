import type {
  CommentDto,
  CurrentPerformanceDto,
  GameSnapshotDto,
  PlayerDto,
  RankingEntry,
  ScoreResultDto,
  SessionInfo,
  VideoDto,
} from 'contracts';

export type PhonePlaygroundScenario =
  | 'home'
  | 'create'
  | 'join'
  | 'lobby-host'
  | 'lobby-guest'
  | 'idle'
  | 'performer-confirm'
  | 'performer-running'
  | 'volunteer'
  | 'judge'
  | 'audience'
  | 'locked'
  | 'results';

export interface PhoneScenarioConfig {
  id: PhonePlaygroundScenario;
  label: string;
  view: 'home' | 'create' | 'join' | 'lobby' | 'performance' | 'results';
}

export const PHONE_SCENARIOS: PhoneScenarioConfig[] = [
  { id: 'home', label: 'Home', view: 'home' },
  { id: 'create', label: 'Create', view: 'create' },
  { id: 'join', label: 'Join', view: 'join' },
  { id: 'lobby-host', label: 'Lobby (Host)', view: 'lobby' },
  { id: 'lobby-guest', label: 'Lobby (Guest)', view: 'lobby' },
  { id: 'idle', label: 'Queue / Idle', view: 'performance' },
  { id: 'performer-confirm', label: 'Performer Confirm', view: 'performance' },
  { id: 'performer-running', label: 'Performing', view: 'performance' },
  { id: 'volunteer', label: 'Volunteer', view: 'performance' },
  { id: 'judge', label: 'Judge', view: 'performance' },
  { id: 'audience', label: 'Audience', view: 'performance' },
  { id: 'locked', label: 'Round Results', view: 'performance' },
  { id: 'results', label: 'Game Over', view: 'results' },
];

export const generateMockPlayers = (count: number): PlayerDto[] => {
  return Array.from({ length: count }, (_, i) => ({
    playerId: `player${i + 1}-id`,
    displayName: `Player ${i + 1}`,
    pictureUrl: null,
    score: (count - i) * 10,
    isHost: i === 0,
    onBreak: false,
  }));
};

export const generateMockRanking = (players: PlayerDto[]): RankingEntry[] => {
  return [...players]
    .sort((a, b) => b.score - a.score)
    .map((p, i) => ({
      rank: i + 1,
      playerId: p.playerId,
      displayName: p.displayName,
      score: p.score,
    }));
};

export const generateMockComments = (count: number, players: PlayerDto[]): CommentDto[] => {
  const now = Date.now();
  return Array.from({ length: count }, (_, i) => {
    const author = players.length > 0 ? players[i % players.length] : null;
    return {
      commentId: `comment-${i + 1}`,
      authorPlayerId: author?.playerId ?? 'player1-id',
      authorName: author?.displayName ?? 'Player 1',
      body: `text${i + 1}`,
      createdAt: new Date(now - (count - i) * 5000).toISOString(),
      likeCount: i % 3,
      likedByMe: false,
    };
  });
};

export const MOCK_VIDEOS_1: VideoDto[] = [
  {
    videoId: 'vid-1',
    youtubeUrl: null,
    hasLocalFile: true,
    videoName: 'Queen - Bohemian Rhapsody',
    thumbnailUrl: null,
    songTitle: 'Bohemian Rhapsody',
    artist: 'Queen',
    durationSeconds: 355,
  },
  {
    videoId: 'vid-2',
    youtubeUrl: 'https://youtu.be/ZqY3eONjX5k',
    hasLocalFile: true,
    videoName: 'Taylor Swift - Love Story Backing Track',
    thumbnailUrl: null,
    songTitle: 'Love Story Backing Track',
    artist: 'Taylor Swift',
    durationSeconds: 254,
  },
  {
    videoId: 'vid-3',
    youtubeUrl: 'https://youtu.be/dQw4w9WgXcQ',
    hasLocalFile: false,
    videoName: 'Rick Astley - Never Gonna Give You Up (Karaoke)',
    thumbnailUrl: null,
    songTitle: 'Never Gonna Give You Up',
    artist: 'Rick Astley',
    durationSeconds: 213,
  },
  {
    videoId: 'vid-4',
    youtubeUrl: null,
    hasLocalFile: true,
    videoName: 'Radiohead - Creep',
    thumbnailUrl: null,
    songTitle: 'Creep',
    artist: 'Radiohead',
    durationSeconds: 257,
  },
  {
    videoId: 'vid-5',
    youtubeUrl: null,
    hasLocalFile: true,
    videoName: 'Some Other Artist - Some Song',
    thumbnailUrl: null,
    songTitle: 'Some Song',
    artist: 'Some Other Artist',
    durationSeconds: 999,
  }
];

export const MOCK_VIDEOS_2: VideoDto[] = [
  {
    videoId: 'vid-6',
    youtubeUrl: null,
    hasLocalFile: true,
    videoName: 'Some Other Artist - Some Song',
    thumbnailUrl: null,
    songTitle: 'Some Song',
    artist: 'Some Other Artist',
    durationSeconds: 999,
  },
  {
    videoId: 'vid-7',
    youtubeUrl: null,
    hasLocalFile: true,
    videoName: 'Some Other Artist - Some Song',
    thumbnailUrl: null,
    songTitle: 'Some Song',
    artist: 'Some Other Artist',
    durationSeconds: 999,
  }
];

export const generateMockLockedScores = (players: PlayerDto[]): ScoreResultDto[] => {
  return players.slice(0, 2).map((p, idx) => ({
    playerId: p.playerId,
    displayName: p.displayName,
    points: 88 - idx * 6,
  }));
};

export const buildMockSession = (
  scenario: PhonePlaygroundScenario,
  players: PlayerDto[],
): SessionInfo => {
  // Player 1 = Host / Performer, Player 3 = Judge, Player 5 = Audience / Guest
  let player = players[0];
  if (scenario === 'lobby-guest' || scenario === 'audience' || scenario === 'volunteer') {
    player = players[4] ?? players[0];
  } else if (scenario === 'judge') {
    player = players[2] ?? players[0];
  }

  return {
    gameId: 'mock-game-id',
    token: 'mock-session-token',
    displayToken: 'mock-display-token',
    isHost: player.isHost,
    playerId: player.playerId,
    displayName: player.displayName,
  };
};

export const buildMockPhoneSnapshot = (
  scenario: PhonePlaygroundScenario,
  players: PlayerDto[],
): GameSnapshotDto => {
  const now = Date.now();
  const isLobby = scenario === 'lobby-host' || scenario === 'lobby-guest';
  const rankingEntries = isLobby ? [] : generateMockRanking(players);

  const performers = players.slice(0, 2);
  const judges = players.slice(2, 4).map((p) => p.playerId);

  let state: GameSnapshotDto['state'] = 'ACTIVE';
  let currentPerformance: CurrentPerformanceDto | null = null;

  if (isLobby || scenario === 'home' || scenario === 'create' || scenario === 'join') {
    state = 'CREATED';
  } else if (scenario === 'results') {
    state = 'OVER';
  } else if (scenario === 'performer-confirm' || scenario === 'volunteer') {
    currentPerformance = {
      performanceId: 1,
      type: 'KARAOKE',
      state: 'CONFIRMING',
      videoId: 'vid-1',
      youtubeUrl: null,
      confirmDeadlineAt: new Date(now + 25_000).toISOString(),
      // Volunteer scenario has replacement window already open
      replacementOpensAt: new Date(now + (scenario === 'volunteer' ? -5_000 : 15_000)).toISOString(),
      slots: performers.map((p, idx) => ({
        slotId: `slot-${idx + 1}`,
        slotIndex: idx,
        currentPlayerId: p.playerId,
        currentPlayerName: p.displayName,
        state: idx === 0 ? 'PENDING' : 'CONFIRMED',
      })),
      judgePlayerIds: judges,
      durationSeconds: 254,
      startedAt: null,
      judgingDeadlineAt: null,
    };
  } else if (scenario === 'performer-running' || scenario === 'judge' || scenario === 'audience') {
    currentPerformance = {
      performanceId: 2,
      type: 'KARAOKE',
      state: 'RUNNING',
      videoId: 'vid-1',
      youtubeUrl: null,
      confirmDeadlineAt: null,
      replacementOpensAt: null,
      slots: performers.map((p, idx) => ({
        slotId: `slot-${idx + 1}`,
        slotIndex: idx,
        currentPlayerId: p.playerId,
        currentPlayerName: p.displayName,
        state: 'CONFIRMED',
      })),
      judgePlayerIds: judges,
      durationSeconds: 254,
      startedAt: new Date(now - 30_000).toISOString(),
      judgingDeadlineAt: new Date(now + 254_000).toISOString(),
    };
  } else if (scenario === 'locked') {
    currentPerformance = {
      performanceId: 3,
      type: 'KARAOKE',
      state: 'LOCKED',
      videoId: 'vid-1',
      youtubeUrl: null,
      confirmDeadlineAt: null,
      replacementOpensAt: null,
      slots: performers.map((p, idx) => ({
        slotId: `slot-${idx + 1}`,
        slotIndex: idx,
        currentPlayerId: p.playerId,
        currentPlayerName: p.displayName,
        state: 'CONFIRMED',
      })),
      judgePlayerIds: judges,
      durationSeconds: 254,
      startedAt: new Date(now - 260_000).toISOString(),
      judgingDeadlineAt: new Date(now - 5_000).toISOString(),
    };
  }

  return {
    gameId: 'mock-game-id',
    joinCode: 'MOCK00',
    joinCodeDisplay: 'MOC K00',
    state,
    players,
    currentPerformance,
    ranking: {
      page: 0,
      pageSize: 5,
      totalPlayers: players.length,
      entries: rankingEntries,
    },
    queueNonEmpty: false,
  };
};
