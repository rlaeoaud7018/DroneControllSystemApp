package com.dronecontrol.internal;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dronecontrol.stats.StatSample;
import com.dronecontrol.stats.StatsService;

/** analysis-server가 초 단위 분석 결과를 보내는 내부 전용 API. 외부 노출 금지(운영 시 nginx에서 차단). */
@RestController
@RequestMapping("/internal/v1/stats")
public class InternalStatsController {

	@Autowired
	private StatsService statsService;

	@PostMapping
	public ResponseEntity<?> receive(@RequestBody List<StatSample> samples) {
		if (samples == null || samples.isEmpty()) {
			return ResponseEntity.noContent().build();
		}

		int successCount = 0;
		for (StatSample sample : samples) {
			try {
				if (statsService.recordSample(sample)) {
					successCount++;
				}
			} catch (Exception e) {
				System.out.println("[InternalStatsController] 샘플 처리 실패: " + e.getMessage());
			}
		}

		if (successCount == 0) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "없는 캠입니다."));
		}

		return ResponseEntity.noContent().build();
	}

}
