package com.karaogui.backend.game;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 0 placeholder for the snapshot half of the snapshot+stream pattern (T02 §4.1).
 * Returns an empty shell so the web layer and routing are verifiable before the domain
 * is built out in Phase 1.
 */
@RestController
@RequestMapping("/api/games")
class GameSnapshotController {

	@GetMapping("/{gameId}")
	Map<String, Object> snapshot(@PathVariable String gameId) {
		return Map.of(
				"gameId", gameId,
				"state", "CREATED",
				"players", List.of(),
				"currentPerformance", Map.of(),
				"ranking", List.of());
	}
}
