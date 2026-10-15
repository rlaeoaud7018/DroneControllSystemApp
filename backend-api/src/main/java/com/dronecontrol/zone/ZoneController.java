package com.dronecontrol.zone;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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

import com.dronecontrol.event.EventService;
import com.dronecontrol.event.EventThreshold;
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
