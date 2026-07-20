import { Injectable, signal, Signal, computed } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import { Subject } from 'rxjs';
import {
  CommentDto,
  CommentLikedData,
  CommentPostedData,
  CurrentPerformanceDto,
  GameEndedData,
  GameEventEnvelope,
  GameSnapshotDto,
  PerformanceAnnouncedData,
  PerformanceLockedData,
  PerformanceSkippedData,
  PerformanceSlotDto,
  PerformanceStartedData,
  PlayerDto,
  PlayerJoinedData,
  RankingEntry,
  RankingUpdatedData,
  SlotStateChangedData,
} from 'contracts';

@Injectable({ providedIn: 'root' })
export class RealtimeService {
  client: Client | null = null;
  private gameId: string | null = null;

  private _players = signal<PlayerDto[]>([]);
  private _gameState = signal<string>('CREATED');
  private _ranking = signal<RankingEntry[]>([]);
  private _currentPerformance = signal<CurrentPerformanceDto | null>(null);
  private _judgeIds = signal<string[]>([]);
  private _gameEnded = signal<boolean>(false);
  private _queueNonEmpty = signal<boolean>(false);
  private _comments = signal<CommentDto[]>([]);
  private _lockedScores = signal<{ playerId: string; displayName: string; points: number }[]>([]);
  private _topicSeq = new Map<string, number>();

  readonly players$: Signal<PlayerDto[]> = this._players.asReadonly();
  readonly gameState$: Signal<string> = this._gameState.asReadonly();
  readonly ranking$: Signal<RankingEntry[]> = this._ranking.asReadonly();
  readonly currentPerformance$: Signal<CurrentPerformanceDto | null> = this._currentPerformance.asReadonly();
  readonly judgeIds$: Signal<string[]> = this._judgeIds.asReadonly();
  readonly gameEnded$: Signal<boolean> = this._gameEnded.asReadonly();
  readonly queueNonEmpty$: Signal<boolean> = this._queueNonEmpty.asReadonly();
  readonly comments$: Signal<CommentDto[]> = this._comments.asReadonly();
  readonly lockedScores$: Signal<{ playerId: string; displayName: string; points: number }[]> = this._lockedScores.asReadonly();

  readonly resnap$ = new Subject<void>();

  connect(gameId: string, token: string, surface: 'PHONE' | 'TV'): void {
    this.gameId = gameId;
    this._topicSeq.clear();

    this.client = new Client({
      brokerURL: `ws://${window.location.hostname}:8080/ws`,
      connectHeaders: {
        Authorization: `Bearer ${token}`,
        surface,
      },
      reconnectDelay: 3000,
      onConnect: () => {
        this._subscribe(gameId);
      },
    });

    this.client.activate();
  }

  applySnapshot(snap: GameSnapshotDto): void {
    this._players.set(snap.players);
    this._gameState.set(snap.state);
    this._ranking.set(snap.ranking?.entries ?? []);
    const perf = snap.currentPerformance;
    this._currentPerformance.set(perf && perf.performanceId ? perf : null);
    this._judgeIds.set(perf?.judgePlayerIds ?? []);
    this._gameEnded.set(snap.state === 'OVER');
    this._queueNonEmpty.set(snap.queueNonEmpty ?? false);
  }

  prependComments(comments: CommentDto[]): void {
    this._comments.update(prev => [...comments, ...prev].slice(0, 100));
  }

  appendComments(comments: CommentDto[]): void {
    this._comments.update(prev => [...prev, ...comments].slice(0, 200));
  }

  disconnect(): void {
    this.client?.deactivate();
    this.client = null;
    this.gameId = null;
  }

  private _subscribe(gameId: string): void {
    if (!this.client) return;
    const base = `/topic/games/${gameId}`;

    this.client.subscribe(`${base}/players`, (msg: IMessage) => {
      const event: GameEventEnvelope<PlayerJoinedData> = JSON.parse(msg.body);
      if (this._checkSeq('players', event.seq)) {
        if (event.type === 'PLAYER_JOINED') {
          this._players.update((prev) => [
            ...prev,
            {
              playerId: event.data.playerId,
              displayName: event.data.displayName,
              pictureUrl: null,
              score: 0,
              isHost: false,
              onBreak: false,
            },
          ]);
        }
      }
    });

    this.client.subscribe(`${base}/state`, (msg: IMessage) => {
      const event: GameEventEnvelope = JSON.parse(msg.body);
      if (this._checkSeq('state', event.seq)) {
        if (event.type === 'GAME_STARTED') {
          this._gameState.set('ACTIVE');
        } else if (event.type === 'GAME_ENDED') {
          this._gameState.set('OVER');
          this._gameEnded.set(true);
        }
      }
    });

    this.client.subscribe(`${base}/ranking`, (msg: IMessage) => {
      const event: GameEventEnvelope<RankingUpdatedData> = JSON.parse(msg.body);
      if (this._checkSeq('ranking', event.seq)) {
        if (event.type === 'RANKING_UPDATED') {
          this._ranking.set(event.data.entries ?? []);
        }
      }
    });

    this.client.subscribe(`${base}/performers`, (msg: IMessage) => {
      const event: GameEventEnvelope = JSON.parse(msg.body);
      if (!this._checkSeq('performers', event.seq)) return;

      switch (event.type) {
        case 'PERFORMANCE_ANNOUNCED': {
          const d = event.data as PerformanceAnnouncedData;
          this._currentPerformance.set({
            performanceId: d.performanceId,
            type: d.type,
            state: 'CONFIRMING',
            youtubeUrl: d.youtubeUrl,
            confirmDeadlineAt: d.confirmDeadlineAt,
            replacementOpensAt: null,
            slots: d.slots,
            judgePlayerIds: d.judgePlayerIds,
            durationSeconds: null,
            judgingDeadlineAt: null,
            startedAt: null,
          });
          this._judgeIds.set(d.judgePlayerIds);
          this._lockedScores.set([]);
          break;
        }
        case 'SLOT_STATE_CHANGED': {
          const d = event.data as SlotStateChangedData;
          this._currentPerformance.update(perf => {
            if (!perf) return perf;
            return {
              ...perf,
              slots: perf.slots.map(s =>
                s.slotId === d.slotId
                  ? { ...s, state: d.slotState as PerformanceSlotDto['state'], currentPlayerName: d.currentPlayerName }
                  : s,
              ),
            };
          });
          break;
        }
        case 'PERFORMANCE_STARTED': {
          this._currentPerformance.update(perf => perf ? { ...perf, state: 'RUNNING' } : perf);
          this.resnap$.next();
          break;
        }
        case 'PERFORMANCE_LOCKED': {
          const d = event.data as PerformanceLockedData;
          this._currentPerformance.update(perf => perf ? { ...perf, state: 'LOCKED' } : perf);
          this._lockedScores.set(d.scores ?? []);
          this._queueNonEmpty.set(false);
          this._judgeIds.set([]);
          break;
        }
        case 'PERFORMANCE_SKIPPED': {
          this._currentPerformance.set(null);
          this._lockedScores.set([]);
          this._judgeIds.set([]);
          this._queueNonEmpty.set(false);
          break;
        }
      }
    });

    this.client.subscribe(`${base}/comments`, (msg: IMessage) => {
      const event: GameEventEnvelope = JSON.parse(msg.body);
      if (!this._checkSeq('comments', event.seq)) return;

      if (event.type === 'COMMENT_POSTED') {
        const d = event.data as CommentPostedData;
        const comment: CommentDto = {
          commentId: d.commentId,
          authorPlayerId: d.authorPlayerId,
          authorName: d.authorName,
          body: d.body,
          createdAt: d.createdAt,
          likeCount: 0,
          likedByMe: false,
        };
        this._comments.update(prev => [comment, ...prev].slice(0, 100));
      } else if (event.type === 'COMMENT_LIKED') {
        const d = event.data as CommentLikedData;
        this._comments.update(prev =>
          prev.map(c => c.commentId === d.commentId ? { ...c, likeCount: d.likeCount } : c)
        );
      }
    });
  }

  private _checkSeq(topic: string, seq: number): boolean {
    const last = this._topicSeq.get(topic) ?? -1;
    if (seq > last) {
      this._topicSeq.set(topic, seq);
      return true;
    }
    return false;
  }
}
