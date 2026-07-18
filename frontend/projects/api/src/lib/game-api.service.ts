import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import type {
  CreateGameResponse,
  GameSnapshotDto,
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

  private authHeaders(token: string): HttpHeaders {
    return new HttpHeaders({ Authorization: `Bearer ${token}` });
  }
}
