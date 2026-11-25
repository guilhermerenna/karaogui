import { Injectable, inject, isDevMode } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import type {
  CommentDto,
  CreateGameResponse,
  CurrentPerformanceDto,
  EvaluateRequest,
  GameSnapshotDto,
  ImportVideoRequest,
  QueuePerformanceRequest,
  RateRequest,
  VideoDto,
  VideoSearchResponse,
} from 'contracts';

export function resolveApiBase(): string {
  return isDevMode() ? `http://${window.location.hostname}:8080/api` : '/api';
}

@Injectable({ providedIn: 'root' })
export class GameApiService {
  private http = inject(HttpClient);
  private readonly base = resolveApiBase();

  createGame(displayName: string, tvCode?: string): Observable<CreateGameResponse> {
    return this.http.post<CreateGameResponse>(`${this.base}/games`, {
      host: { displayName },
      ...(tvCode ? { tvCode } : {}),
    });
  }

  registerTv(): Observable<{ joinCode: string; joinCodeDisplay: string; displayToken: string }> {
    return this.http.post<{ joinCode: string; joinCodeDisplay: string; displayToken: string }>(
      `${this.base}/tv/register`, {}
    );
  }

  joinGame(joinCode: string, displayName: string): Observable<CreateGameResponse> {
    return this.http.post<CreateGameResponse>(`${this.base}/games/join`, {
      joinCode,
      player: { displayName },
    });
  }

  startGame(gameId: string, token: string): Observable<GameSnapshotDto> {
    return this.http.post<GameSnapshotDto>(
      `${this.base}/games/${gameId}/start`,
      null,
      { headers: this.authHeaders(token) },
    );
  }

  getSnapshot(gameId: string, token: string): Observable<GameSnapshotDto> {
    return this.http.get<GameSnapshotDto>(`${this.base}/games/${gameId}`, {
      headers: this.authHeaders(token),
    });
  }

  getSnapshotByCode(joinCode: string, token: string): Observable<GameSnapshotDto> {
    return this.http.get<GameSnapshotDto>(`${this.base}/games/by-code/${joinCode}`, {
      headers: this.authHeaders(token),
    });
  }

  queuePerformance(gameId: string, token: string, req: QueuePerformanceRequest): Observable<CurrentPerformanceDto> {
    return this.http.post<CurrentPerformanceDto>(
      `${this.base}/games/${gameId}/performances`,
      req,
      { headers: this.authHeaders(token) },
    );
  }

  confirmSlot(gameId: string, perfId: number, token: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}/games/${gameId}/performances/${perfId}/confirm`,
      null,
      { headers: this.authHeaders(token) },
    );
  }

  volunteerSlot(gameId: string, perfId: number, token: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}/games/${gameId}/performances/${perfId}/volunteer`,
      null,
      { headers: this.authHeaders(token) },
    );
  }

  submitEvaluation(gameId: string, perfId: number, token: string, req: EvaluateRequest): Observable<void> {
    return this.http.post<void>(
      `${this.base}/games/${gameId}/performances/${perfId}/evaluate`,
      req,
      { headers: this.authHeaders(token) },
    );
  }

  submitRating(gameId: string, perfId: number, token: string, req: RateRequest): Observable<void> {
    return this.http.post<void>(
      `${this.base}/games/${gameId}/performances/${perfId}/rate`,
      req,
      { headers: this.authHeaders(token) },
    );
  }

  endGame(gameId: string, token: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}/games/${gameId}/end`,
      null,
      { headers: this.authHeaders(token) },
    );
  }

  postComment(gameId: string, token: string, body: string): Observable<CommentDto> {
    return this.http.post<CommentDto>(
      `${this.base}/games/${gameId}/comments`,
      { body },
      { headers: this.authHeaders(token) },
    );
  }

  likeComment(gameId: string, token: string, commentId: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}/games/${gameId}/comments/${commentId}/like`,
      null,
      { headers: this.authHeaders(token) },
    );
  }

  getComments(gameId: string, token: string, page = 0): Observable<CommentDto[]> {
    return this.http.get<CommentDto[]>(
      `${this.base}/games/${gameId}/comments?page=${page}`,
      { headers: this.authHeaders(token) },
    );
  }

  searchVideos(token: string, q = '', page = 0): Observable<VideoSearchResponse> {
    const params = new URLSearchParams({ q, page: String(page) });
    return this.http.get<VideoSearchResponse>(
      `${this.base}/videos?${params.toString()}`,
      { headers: this.authHeaders(token) },
    );
  }

  importVideo(token: string, req: ImportVideoRequest): Observable<VideoDto> {
    return this.http.post<VideoDto>(
      `${this.base}/videos`,
      req,
      { headers: this.authHeaders(token) },
    );
  }

  deleteVideo(token: string, videoId: string): Observable<void> {
    return this.http.delete<void>(
      `${this.base}/videos/${videoId}`,
      { headers: this.authHeaders(token) },
    );
  }

  getVideoStreamUrl(videoId: string): string {
    return `${this.base}/videos/${videoId}/stream`;
  }

  private authHeaders(token: string): HttpHeaders {
    return new HttpHeaders({ Authorization: `Bearer ${token}` });
  }
}
