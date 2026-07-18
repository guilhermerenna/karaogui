import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import type {
  CommentDto,
  CreateGameResponse,
  CurrentPerformanceDto,
  EvaluateRequest,
  GameSnapshotDto,
  QueuePerformanceRequest,
  RateRequest,
} from 'contracts';

const BASE = 'http://localhost:8080/api';

@Injectable({ providedIn: 'root' })
export class GameApiService {
  private http = inject(HttpClient);

  createGame(displayName: string): Observable<CreateGameResponse> {
    return this.http.post<CreateGameResponse>(`${BASE}/games`, {
      host: { displayName },
    });
  }

  joinGame(joinCode: string, displayName: string): Observable<CreateGameResponse> {
    return this.http.post<CreateGameResponse>(`${BASE}/games/join`, {
      joinCode,
      player: { displayName },
    });
  }

  startGame(gameId: string, token: string): Observable<GameSnapshotDto> {
    return this.http.post<GameSnapshotDto>(
      `${BASE}/games/${gameId}/start`,
      null,
      { headers: this.authHeaders(token) },
    );
  }

  getSnapshot(gameId: string, token: string): Observable<GameSnapshotDto> {
    return this.http.get<GameSnapshotDto>(`${BASE}/games/${gameId}`, {
      headers: this.authHeaders(token),
    });
  }

  queuePerformance(gameId: string, token: string, req: QueuePerformanceRequest): Observable<CurrentPerformanceDto> {
    return this.http.post<CurrentPerformanceDto>(
      `${BASE}/games/${gameId}/performances`,
      req,
      { headers: this.authHeaders(token) },
    );
  }

  confirmSlot(gameId: string, perfId: number, token: string): Observable<void> {
    return this.http.post<void>(
      `${BASE}/games/${gameId}/performances/${perfId}/confirm`,
      null,
      { headers: this.authHeaders(token) },
    );
  }

  volunteerSlot(gameId: string, perfId: number, token: string): Observable<void> {
    return this.http.post<void>(
      `${BASE}/games/${gameId}/performances/${perfId}/volunteer`,
      null,
      { headers: this.authHeaders(token) },
    );
  }

  submitEvaluation(gameId: string, perfId: number, token: string, req: EvaluateRequest): Observable<void> {
    return this.http.post<void>(
      `${BASE}/games/${gameId}/performances/${perfId}/evaluate`,
      req,
      { headers: this.authHeaders(token) },
    );
  }

  submitRating(gameId: string, perfId: number, token: string, req: RateRequest): Observable<void> {
    return this.http.post<void>(
      `${BASE}/games/${gameId}/performances/${perfId}/rate`,
      req,
      { headers: this.authHeaders(token) },
    );
  }

  endGame(gameId: string, token: string): Observable<void> {
    return this.http.post<void>(
      `${BASE}/games/${gameId}/end`,
      null,
      { headers: this.authHeaders(token) },
    );
  }

  postComment(gameId: string, token: string, body: string): Observable<CommentDto> {
    return this.http.post<CommentDto>(
      `${BASE}/games/${gameId}/comments`,
      { body },
      { headers: this.authHeaders(token) },
    );
  }

  likeComment(gameId: string, token: string, commentId: string): Observable<void> {
    return this.http.post<void>(
      `${BASE}/games/${gameId}/comments/${commentId}/like`,
      null,
      { headers: this.authHeaders(token) },
    );
  }

  getComments(gameId: string, token: string, page = 0): Observable<CommentDto[]> {
    return this.http.get<CommentDto[]>(
      `${BASE}/games/${gameId}/comments?page=${page}`,
      { headers: this.authHeaders(token) },
    );
  }

  private authHeaders(token: string): HttpHeaders {
    return new HttpHeaders({ Authorization: `Bearer ${token}` });
  }
}
