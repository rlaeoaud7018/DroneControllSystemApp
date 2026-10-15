package com.dronecontrol.camera;

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

import com.dronecontrol.camera.CameraService.UpdateResult;

@RestController
@RequestMapping("/api/v1/cameras")
public class CameraController {

	@Autowired
	private CameraService cameraService;

	@GetMapping
	public List<Camera> list(@RequestParam int zoneId) {
		return cameraService.listCamerasByZoneId(zoneId);
	}

	@GetMapping("/{id}")
	public ResponseEntity<?> get(@PathVariable int id) {
		Camera camera = cameraService.getCamera(id);
		if (camera == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "없는 캠입니다."));
		}
		return ResponseEntity.ok(camera);
	}

	@PostMapping
	public ResponseEntity<?> create(@RequestBody Camera camera) {
		Camera created = cameraService.createCamera(camera);
		if (created == null) {
			return ResponseEntity.badRequest().body(Map.of("message", "캠 생성에 실패했습니다. (없는 구역이거나 잘못된 입력 정보)"));
		}
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@PatchMapping("/{id}")
	public ResponseEntity<?> update(@PathVariable int id, @RequestBody Camera patch) {
		UpdateResult result = cameraService.updateCamera(id, patch);
		return toResponse(result, "없는 캠입니다.", "가동 중인 캠의 입력 정보는 변경할 수 없습니다.");
	}

	@PatchMapping("/{id}/state")
	public ResponseEntity<?> changeState(@PathVariable int id, @RequestBody Map<String, String> body) {
		UpdateResult result = cameraService.changeState(id, body.get("state"));

		if (result.getCode() == UpdateResult.Code.BAD_REQUEST) {
			return ResponseEntity.badRequest().body(Map.of("message", "state는 RUNNING 또는 STOPPED만 가능합니다."));
		}
		if (result.getCode() == UpdateResult.Code.ANALYSIS_SERVER_UNAVAILABLE) {
			return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", "analysis-server에 분석 시작을 요청하지 못했습니다."));
		}
		return toResponse(result, "없는 캠입니다.", "가동 중인 캠의 입력 정보는 변경할 수 없습니다.");
	}

	private ResponseEntity<?> toResponse(UpdateResult result, String notFoundMessage, String conflictMessage) {
		switch (result.getCode()) {
			case OK:
				return ResponseEntity.ok(result.getCamera());
			case NOT_FOUND:
				return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", notFoundMessage));
			case RUNNING_CONFLICT:
			default:
				return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", conflictMessage));
		}
	}

}
