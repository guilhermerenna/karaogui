import { Injectable, signal, Signal, computed } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import { Subject } from 'rxjs';
import {
  GameEventEnvelope,
  GameSnapshotDto,
  PlayerDto,
  PlayerJoinedData,
  RankingEntry,
  RankingUpdatedData,
} from 'contracts';

@Injectable({ providedIn: 'root' })
export class RealtimeService {
  private client: Client | null = null;
  private gameId: string | null = null;

  private _players = signal<PlayerDto[]>([]);
  private _gameState = signal<string>('CREATED');
  private _ranking = signal<RankingEntry[]>([]);
  private _lastSeq = -1;

  readonly players$: Signal<PlayerDto[]> = this._players.asReadonly();
  readonly gameState$: Signal<string> = this._gameState.asReadonly();
  readonly ranking$: Signal<RankingEntry[]> = this._ranking.asReadonly();

  readonly resnap$ = new Subject<void>();

  connect(gameId: string, token: string, surface: 'PHONE' | 'TV'): void {
    this.gameId = gameId;
    this._lastSeq = -1;

    this.client = new Client({
      brokerURL: 'ws://localhost:8080/ws',
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
      if (this._checkSeq(event.seq)) {
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
      if (this._checkSeq(event.seq)) {
        if (event.type === 'GAME_STARTED') {
          this._gameState.set('ACTIVE');
        }
      }
    });

    this.client.subscribe(`${base}/ranking`, (msg: IMessage) => {
      const event: GameEventEnvelope<RankingUpdatedData> = JSON.parse(msg.body);
      if (this._checkSeq(event.seq)) {
        if (event.type === 'RANKING_UPDATED') {
          this._ranking.set(event.data.entries ?? []);
        }
      }
    });
  }

  private _checkSeq(seq: number): boolean {
    if (this._lastSeq === -1 || seq === this._lastSeq + 1) {
      this._lastSeq = seq;
      return true;
    }
    if (seq > this._lastSeq + 1) {
      this.resnap$.next();
    }
    return false;
  }
}
