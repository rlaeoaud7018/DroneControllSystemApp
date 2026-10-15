package com.dronecontrol.history;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 작업 기록 조회. 명세서 기준 최고관리자 전용(histre-001). */
@RestController
@RequestMapping("/api/v1/history")
public class WorkHistoryController {

	@Autowired
	private WorkHistoryService workHistoryService;

	@GetMapping
	public ResponseEntity<?> list(@RequestParam(required = false) Integer adminId,
			@RequestParam(required = false) String actionType, HttpSession session) {

		String role = (String) session.getAttribute("userRole");
		if (!"SUPER_ADMIN".equals(role)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "최고관리자만 조회할 수 있습니다."));
		}

		List<WorkHistory> history = workHistoryService.list(adminId, actionType);
		return ResponseEntity.ok(history);
	}

}
