package com.dronecontrol.zone;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.dronecontrol.event.EventService;
import com.dronecontrol.event.EventThreshold;
import com.dronecontrol.recording.Recording;
import com.dronecontrol.recording.RecordingService;
import com.dronecontrol.stats.StatsExportService;
import com.dronecontrol.stats.StatsService;
import com.dronecontrol.stats.ZoneMinuteStat;

@RestController
@RequestMapping("/api/v1/zones")
public class ZoneController {

	@Autowired
	private ZoneService zoneService;

	@Autowired
	private StatsService statsService;

	@Autowired
	private EventService eventService;

	@Autowired
	private StatsExportService statsExportService;

	@Autowired
	private RecordingService recordingService;

	@GetMapping
	public List<Zone> list(@RequestParam int dongId) {
		return zoneService.listZonesByDongId(dongId);
	}

	@GetMapping("/{id}")
	public ResponseEntity<?> get(@PathVariable int id) {
		Zone zone = zoneService.getZone(id);
		if (zone == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "없는 구역입니다."));
		}
		return ResponseEntity.ok(zone);
	}

	@PostMapping
	public ResponseEntity<?> create(@RequestBody Zone zone) {
		Zone created = zoneService.createZone(zone);
		if (created == null) {
			return ResponseEntity.badRequest().body(Map.of("message", "구역 생성에 실패했습니다. (없는 동 지역이거나 이름 중복)"));
		}
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@PatchMapping("/{id}")
	public ResponseEntity<?> update(@PathVariable int id, @RequestBody Zone patch) {
		Zone updated = zoneService.updateZone(id, patch);
		if (updated == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "없는 구역입니다."));
		}
		return ResponseEntity.ok(updated);
	}

	@PutMapping("/{id}/neighbors")
	public ResponseEntity<?> setNeighbor(@PathVariable int id, @RequestBody ZoneNeighbor neighbor) {
		List<ZoneNeighbor> neighbors = zoneService.setNeighbor(id, neighbor);
		if (neighbors == null) {
			return ResponseEntity.badRequest().body(Map.of("message", "없는 구역이거나 자기 자신을 지정했습니다."));
		}
		return ResponseEntity.ok(neighbors);
	}

	@GetMapping("/{id}/neighbors")
	public List<ZoneNeighbor> getNeighbors(@PathVariable int id) {
		return zoneService.getNeighbors(id);
	}

	@GetMapping("/{id}/stats")
	public List<ZoneMinuteStat> getStats(@PathVariable int id,
			@RequestParam String from, @RequestParam String to) {
		return statsService.getMinuteStats(id, from, to);
	}

	/** 분단위 통계 엑셀 다운로드 (export-001). */
	@GetMapping("/{id}/stats/export")
	public ResponseEntity<byte[]> exportStats(@PathVariable int id,
			@RequestParam String from, @RequestParam String to) {
		Zone zone = zoneService.getZone(id);
		if (zone == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
		List<ZoneMinuteStat> stats = statsService.getMinuteStats(id, from, to);
		byte[] xlsx = statsExportService.buildWorkbook(zone.getName(), stats);

		String filename = zone.getName() + "_통계_" + from.substring(0, 10) + "_" + to.substring(0, 10) + ".xlsx";
		ContentDisposition disposition = ContentDisposition.attachment()
				.filename(filename, StandardCharsets.UTF_8)
				.build();

		HttpHeaders headers = new HttpHeaders();
		headers.setContentDisposition(disposition);
		headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
		return ResponseEntity.ok().headers(headers).body(xlsx);
	}

	/** 구역 통계 실시간 스트림 (sse-001). 새 분단위 통계가 집계될 때마다 push. */
	@GetMapping("/{id}/stats/stream")
	public SseEmitter streamStats(@PathVariable int id) {
		return statsService.subscribe(id);
	}

	/** 녹화 재생 — 과거 구간의 녹화(재생) 이력 + 해당 구간 통계 타임라인 (repfocus-001). */
	@GetMapping("/{id}/playback")
	public Map<String, Object> getPlayback(@PathVariable int id,
			@RequestParam String from, @RequestParam String to) {
		List<Recording> recordings = recordingService.getRecordings(id, from, to);
		List<ZoneMinuteStat> timeline = statsService.getMinuteStats(id, from, to);
		return Map.of("recordings", recordings, "timeline", timeline);
	}

	@GetMapping("/{id}/thresholds")
	public List<EventThreshold> getThresholds(@PathVariable int id) {
		return eventService.getThresholds(id);
	}

	@PutMapping("/{id}/thresholds")
	public List<EventThreshold> setThresholds(@PathVariable int id, @RequestBody List<EventThreshold> thresholds) {
		eventService.setThresholds(id, thresholds);
		return eventService.getThresholds(id);
	}

}
