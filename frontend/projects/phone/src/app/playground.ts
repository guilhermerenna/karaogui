import { ChangeDetectionStrategy, Component, OnDestroy, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { GameApiService } from 'api';
import type { CommentDto, GameSnapshotDto, ScoreResultDto, VideoDto } from 'contracts';
import { RealtimeService } from 'realtime';
import { HomeComponent } from './home/home';
import { CreateGameComponent } from './create/create-game';
import { JoinGameComponent } from './join/join-game';
import { LobbyComponent } from './lobby/lobby';
import { PerformanceComponent } from './performance/performance';
import { ResultsComponent } from './results/results';
import {
  MOCK_VIDEOS_1,
  MOCK_VIDEOS_2,
  PHONE_SCENARIOS,
  PhonePlaygroundScenario,
  PhoneScenarioConfig,
  buildMockPhoneSnapshot,
  buildMockSession,
  generateMockComments,
  generateMockLockedScores,
  generateMockPlayers,
} from './test-utils';

const PLAYERS = generateMockPlayers(8);
const COMMENTS = generateMockComments(8, PLAYERS);
const INITIAL_SCENARIO: PhonePlaygroundScenario = 'judge';

class MockPhoneRealtimeService extends RealtimeService {
  private readonly _mockComments = signal<CommentDto[]>(COMMENTS);
  private readonly _mockLockedScores = signal<ScoreResultDto[]>([]);

  override readonly comments$ = this._mockComments.asReadonly();
  override readonly lockedScores$ = this._mockLockedScores.asReadonly();

  currentSnapshot: GameSnapshotDto = buildMockPhoneSnapshot(INITIAL_SCENARIO, PLAYERS);

  loadScenario(scenario: PhonePlaygroundScenario): void {
    const snap = buildMockPhoneSnapshot(scenario, PLAYERS);
    this.currentSnapshot = snap;
    this.applySnapshot(snap);
    this._mockComments.set(
      scenario === 'lobby-host' || scenario === 'lobby-guest' ? [] : [...COMMENTS],
    );
    this._mockLockedScores.set(scenario === 'locked' ? generateMockLockedScores(PLAYERS) : []);
  }

  addLocalComment(authorId: string, authorName: string, body: string): CommentDto {
    const next: CommentDto = {
      commentId: `comment-local-${Date.now()}`,
      authorPlayerId: authorId,
      authorName,
      body,
      createdAt: new Date().toISOString(),
      likeCount: 0,
      likedByMe: false,
    };
    this._mockComments.update((prev) => [next, ...prev]);
    return next;
  }

  toggleLike(commentId: string): void {
    this._mockComments.update((prev) =>
      prev.map((c) => (c.commentId === commentId ? { ...c, likeCount: c.likeCount + 1 } : c)),
    );
  }

  override prependComments(comments: CommentDto[]): void {
    this._mockComments.set(comments);
  }

  override appendComments(comments: CommentDto[]): void {
    this._mockComments.update((prev) => [...prev, ...comments]);
  }

  override connect(): void {}
  override disconnect(): void {}
}

@Component({
  selector: 'app-playground',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    HomeComponent,
    CreateGameComponent,
    JoinGameComponent,
    LobbyComponent,
    PerformanceComponent,
    ResultsComponent,
  ],
  providers: [
    MockPhoneRealtimeService,
    { provide: RealtimeService, useExisting: MockPhoneRealtimeService },
    {
      provide: GameApiService,
      useFactory: (mockRt: MockPhoneRealtimeService) => {
        let page1Videos: VideoDto[] = [...MOCK_VIDEOS_1];
        let page2Videos: VideoDto[] = [...MOCK_VIDEOS_2];
        return {
          getSnapshot: () => of(mockRt.currentSnapshot),
          getSnapshotByCode: () => of(mockRt.currentSnapshot),
          getComments: () => of(mockRt.comments$()),
          searchVideos: (_token: string, q = '', page = 0) => {
            const needle = q.trim().toLowerCase();
            if (needle) {
              const allVideos = [...page1Videos, ...page2Videos];
              const filtered = allVideos.filter((v) =>
                `${v.songTitle ?? ''} ${v.artist ?? ''} ${v.videoName ?? ''}`
                  .toLowerCase()
                  .includes(needle),
              );
              // Split filtered list by page size (e.g. 5) if needed, or paginate based on pages
              const pageSize = 5;
              const start = page * pageSize;
              const content = filtered.slice(start, start + pageSize);
              const hasMore = start + pageSize < filtered.length;
              return of({ content, hasMore });
            }

            if (page === 0) {
              return of({ content: page1Videos, hasMore: page2Videos.length > 0 });
            } else if (page === 1) {
              return of({ content: page2Videos, hasMore: false });
            } else {
              return of({ content: [], hasMore: false });
            }
          },
          postComment: (_gameId: string, _token: string, body: string) => {
            const raw = sessionStorage.getItem('session');
            const s = raw ? JSON.parse(raw) : { playerId: 'player1-id', displayName: 'Player 1' };
            return of(mockRt.addLocalComment(s.playerId, s.displayName, body));
          },
          likeComment: (_gameId: string, _token: string, commentId: string) => {
            mockRt.toggleLike(commentId);
            return of(undefined);
          },
          deleteVideo: (_token: string, videoId: string) => {
            page1Videos = page1Videos.filter((v) => v.videoId !== videoId);
            page2Videos = page2Videos.filter((v) => v.videoId !== videoId);
            return of(undefined);
          },
          importVideo: (_token: string, req: { youtubeUrl: string; songTitle?: string; artist?: string }) => {
            const created: VideoDto = {
              videoId: `vid-${Date.now()}`,
              youtubeUrl: req.youtubeUrl,
              hasLocalFile: false,
              videoName: req.songTitle ?? 'Imported Video',
              thumbnailUrl: null,
              songTitle: req.songTitle ?? null,
              artist: req.artist ?? null,
              durationSeconds: 210,
            };
            page1Videos = [created, ...page1Videos];
            return of(created);
          },
          queuePerformance: () => of(mockRt.currentSnapshot.currentPerformance),
          confirmSlot: () => of(undefined),
          volunteerSlot: () => of(undefined),
          submitEvaluation: () => of(undefined),
          submitRating: () => of(undefined),
          startGame: () => of(mockRt.currentSnapshot),
          endGame: () => of(undefined),
          createGame: () =>
            of({
              gameId: 'mock-game-id',
              joinCode: 'MOCK00',
              joinCodeDisplay: 'MOC K00',
              state: 'CREATED',
              you: { playerId: PLAYERS[0].playerId, displayName: PLAYERS[0].displayName, isHost: true },
              sessionToken: 'mock-session-token',
              displayToken: 'mock-display-token',
            }),
          joinGame: () =>
            of({
              gameId: 'mock-game-id',
              joinCode: 'MOCK00',
              joinCodeDisplay: 'MOC K00',
              state: 'CREATED',
              you: { playerId: PLAYERS[4].playerId, displayName: PLAYERS[4].displayName, isHost: false },
              sessionToken: 'mock-session-token',
              displayToken: null,
            }),
        };
      },
      deps: [MockPhoneRealtimeService],
    },
  ],
  templateUrl: './playground.html',
  styleUrl: './playground.scss',
})
export class Playground implements OnDestroy {
  private readonly mockRt = inject(MockPhoneRealtimeService);
  private readonly router = inject(Router);
  private readonly previousSession: string | null;

  readonly scenarios: PhoneScenarioConfig[] = PHONE_SCENARIOS;
  readonly activeScenario = signal<PhonePlaygroundScenario>(INITIAL_SCENARIO);

  constructor() {
    this.previousSession = sessionStorage.getItem('session');
    this.applyScenario(INITIAL_SCENARIO);
  }

  ngOnDestroy(): void {
    if (this.previousSession !== null) {
      sessionStorage.setItem('session', this.previousSession);
    } else {
      sessionStorage.removeItem('session');
    }
  }

  activeView(): PhoneScenarioConfig['view'] {
    return this.scenarios.find((s) => s.id === this.activeScenario())?.view ?? 'home';
  }

  selectScenario(scenario: PhonePlaygroundScenario): void {
    this.applyScenario(scenario);
    this.activeScenario.set(scenario);
  }

  exitPlayground(): void {
    if (typeof window !== 'undefined') {
      window.playground = false;
    }
    void this.router.navigateByUrl('/');
  }

  private applyScenario(scenario: PhonePlaygroundScenario): void {
    const session = buildMockSession(scenario, PLAYERS);
    sessionStorage.setItem('session', JSON.stringify(session));
    this.mockRt.loadScenario(scenario);
  }
}
