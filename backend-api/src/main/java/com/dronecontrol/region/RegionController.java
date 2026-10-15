package com.dronecontrol.region;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/regions")
public class RegionController {

	@Autowired
	private RegionService regionService;

	@GetMapping
	public List<Region> list(@RequestParam(required = false) Integer parentId) {
		return regionService.listRegions(parentId);
	}

	@GetMapping("/{id}")
	public ResponseEntity<?> get(@PathVariable int id) {
		Region region = regionService.getRegion(id);
		if (region == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "없는 지역입니다."));
		}
		return ResponseEntity.ok(region);
	}

	@PostMapping
	public ResponseEntity<?> create(@RequestBody Region region) {
		Region created = regionService.createRegion(region);
		if (created == null) {
			return ResponseEntity.badRequest().body(Map.of("message", "지역 생성에 실패했습니다."));
		}
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@PatchMapping("/{id}")
	public ResponseEntity<?> update(@PathVariable int id, @RequestBody Region patch) {
		Region updated = regionService.updateRegion(id, patch);
		if (updated == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "없는 지역입니다."));
		}
		return ResponseEntity.ok(updated);
	}

}
