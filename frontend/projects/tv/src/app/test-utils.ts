import type { CommentDto, CurrentPerformanceDto, GameSnapshotDto, PlayerDto, QueuedPerformanceDto, RankingEntry, ScoreResultDto } from 'contracts';

export type PlaygroundScenario = 'running' | 'confirming' | 'locked' | 'lobby' | 'over';

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
      likeCount: 0,
      likedByMe: false,
    };
  });
};

export const generateMockPerformance = (
  state: 'CONFIRMING' | 'RUNNING' | 'LOCKED',
  players: PlayerDto[],
): CurrentPerformanceDto => {
  const now = Date.now();
  const durationSeconds = 180;
  const performers = players.slice(0, 2);
  const judgePlayers = players.slice(2, 4);
  const judges = judgePlayers.map((p) => p.playerId);

  return {
    performanceId: 1,
    type: 'KARAOKE',
    state,
    // Set a local videoId or youtubeUrl here if you want to test playback in the playground.
    videoId: '05be8810-1be4-4155-9818-39114044e85b',
    youtubeUrl: null,
    songTitle: 'Bohemian Rhapsody',
    artist: 'Queen',
    videoName: null,
    confirmDeadlineAt: state === 'CONFIRMING' ? new Date(now + 30_000).toISOString() : null,
    replacementOpensAt: state === 'CONFIRMING' ? new Date(now + 20_000).toISOString() : null,
    slots: performers.map((p, idx) => ({
      slotId: `slot-${idx + 1}`,
      slotIndex: idx,
      currentPlayerId: p.playerId,
      currentPlayerName: p.displayName,
      state: state === 'CONFIRMING' && idx === 1 ? 'PENDING' : 'CONFIRMED',
    })),
    judgePlayerIds: judges,
    judges: judgePlayers.map((p, idx) => ({
      judgePlayerId: p.playerId,
      displayName: p.displayName,
      submitted: state === 'RUNNING' && idx === 0,
    })),
    durationSeconds,
    startedAt: state === 'RUNNING' ? new Date(now - 30_000).toISOString() : null,
    judgingDeadlineAt:
      state === 'RUNNING' ? new Date(now + (durationSeconds - 30 + 30) * 1000).toISOString() : null,
  };
};

export const generateMockLockedScores = (players: PlayerDto[]): ScoreResultDto[] => {
  return players.slice(0, 2).map((p, idx) => ({
    playerId: p.playerId,
    displayName: p.displayName,
    points: 85 - idx * 7,
  }));
};

export const generateMockUpcomingQueue = (players: PlayerDto[]): QueuedPerformanceDto[] => {
  const songs = [
    { songTitle: 'Bohemian Rhapsody', artist: 'Queen' },
    { songTitle: 'Creep', artist: 'Radiohead' },
    { songTitle: 'Mr. Brightside', artist: 'The Killers' },
    { songTitle: 'Don\'t Stop Believin\'', artist: 'Journey' },
  ];
  return songs.map((s, i) => ({
    performanceId: 10 + i,
    queuePosition: i + 1,
    songTitle: s.songTitle,
    artist: s.artist,
    videoName: `${s.artist} - ${s.songTitle}`,
    performerNames: players.length >= 2
      ? [players[i % players.length].displayName, players[(i + 1) % players.length].displayName]
      : ['Player 1'],
  }));
};

export const buildMockSnapshot = (
  scenario: PlaygroundScenario,
  players: PlayerDto[],
): GameSnapshotDto => {
  const isLobby = scenario === 'lobby';
  const rankingEntries = isLobby ? [] : generateMockRanking(players);
  const showQueue = scenario === 'running' || scenario === 'confirming' || scenario === 'locked';
  const upcomingQueue = showQueue ? generateMockUpcomingQueue(players) : [];

  let state: GameSnapshotDto['state'] = 'ACTIVE';
  let currentPerformance: CurrentPerformanceDto | null = null;

  if (scenario === 'lobby') {
    state = 'CREATED';
  } else if (scenario === 'over') {
    state = 'OVER';
  } else if (scenario === 'confirming') {
    currentPerformance = generateMockPerformance('CONFIRMING', players);
  } else if (scenario === 'running') {
    currentPerformance = generateMockPerformance('RUNNING', players);
  } else if (scenario === 'locked') {
    currentPerformance = generateMockPerformance('LOCKED', players);
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
    queueNonEmpty: upcomingQueue.length > 0,
    upcomingQueue,
  };
};