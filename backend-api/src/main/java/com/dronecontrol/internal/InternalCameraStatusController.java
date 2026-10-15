package com.dronecontrol.internal;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dronecontrol.camera.Camera;
import com.dronecontrol.camera.CameraService;

/** analysis-server가 캠 상태/진행률을 보고하는 내부 전용 API. */
@RestController
@RequestMapping("/internal/v1/cameras")
public class InternalCameraStatusController {

	@Autowired
	private CameraService cameraService;

	@PatchMapping("/{id}/status")
	public ResponseEntity<?> reportStatus(@PathVariable int id, @RequestBody CameraStatusReport report) {
		Camera updated = cameraService.reportStatus(id, report.getStatus(), report.getProgress(), report.getErrorMessage());
		if (updated == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "없는 캠입니다."));
		}
		return ResponseEntity.noContent().build();
	}

}
