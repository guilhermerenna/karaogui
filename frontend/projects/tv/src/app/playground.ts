import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { GameApiService } from 'api';
import type { CommentDto, GameSnapshotDto, ScoreResultDto } from 'contracts';
import { RealtimeService } from 'realtime';
import { TvLobbyComponent } from './lobby/tv-lobby';
import {
  PlaygroundScenario,
  buildMockSnapshot,
  generateMockComments,
  generateMockLockedScores,
  generateMockPlayers,
} from './test-utils';

const PLAYERS = generateMockPlayers(12);
const COMMENTS = generateMockComments(12, PLAYERS);
const INITIAL_SCENARIO: PlaygroundScenario = 'running';
const INITIAL_SNAPSHOT: GameSnapshotDto = buildMockSnapshot(INITIAL_SCENARIO, PLAYERS);

class MockRealtimeService extends RealtimeService {
  private readonly _mockComments = signal<CommentDto[]>(COMMENTS);
  private readonly _mockLockedScores = signal<ScoreResultDto[]>([]);

  override readonly comments$ = this._mockComments.asReadonly();
  override readonly lockedScores$ = this._mockLockedScores.asReadonly();

  loadScenario(scenario: PlaygroundScenario): void {
    this.applySnapshot(buildMockSnapshot(scenario, PLAYERS));
    this._mockComments.set(scenario === 'lobby' ? [] : COMMENTS);
    this._mockLockedScores.set(scenario === 'locked' ? generateMockLockedScores(PLAYERS) : []);
  }

  override connect(): void {}
  override disconnect(): void {}
}

@Component({
  selector: 'app-playground',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TvLobbyComponent],
  providers: [
    MockRealtimeService,
    { provide: RealtimeService, useExisting: MockRealtimeService },
    {
      provide: ActivatedRoute,
      useValue: {
        queryParams: of({ jc: INITIAL_SNAPSHOT.joinCode, dt: 'mock-display-token' }),
      },
    },
    {
      provide: GameApiService,
      useValue: {
        getSnapshotByCode: () => of(INITIAL_SNAPSHOT),
        getSnapshot: () => of(INITIAL_SNAPSHOT),
      },
    },
  ],
  templateUrl: './playground.html',
  styleUrl: './playground.scss',
})
export class Playground {
  private readonly mockRt = inject(MockRealtimeService);
  private readonly router = inject(Router);

  readonly scenarios: { id: PlaygroundScenario; label: string }[] = [
    { id: 'running', label: 'Running' },
    { id: 'confirming', label: 'Confirming' },
    { id: 'locked', label: 'Locked' },
    { id: 'lobby', label: 'Lobby' },
    { id: 'over', label: 'Game Over' },
  ];

  readonly activeScenario = signal<PlaygroundScenario>(INITIAL_SCENARIO);

  selectScenario(scenario: PlaygroundScenario): void {
    this.activeScenario.set(scenario);
    this.mockRt.loadScenario(scenario);
  }

  exitPlayground(): void {
    if (typeof window !== 'undefined') {
      window.playground = false;
    }
    void this.router.navigateByUrl('/');
  }
}
