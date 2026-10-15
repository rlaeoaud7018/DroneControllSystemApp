package com.dronecontrol.event;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dronecontrol.history.WorkHistoryService;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

	@Autowired
	private EventService eventService;

	@Autowired
	private WorkHistoryService workHistoryService;

	@GetMapping
	public List<Event> list(@RequestParam(required = false) Integer zoneId,
			@RequestParam(required = false) String status) {
		return eventService.listEvents(zoneId, status);
	}

	@GetMapping("/{id}")
	public ResponseEntity<?> get(@PathVariable int id) {
		Event event = eventService.getEvent(id);
		if (event == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "없는 이벤트입니다."));
		}
		return ResponseEntity.ok(event);
	}

	@PatchMapping("/{id}")
	public ResponseEntity<?> updateStatus(@PathVariable int id, @RequestBody Map<String, String> body, HttpSession session) {
		String status = body.get("status");
		String memo = body.get("memo");

		if (!"CONFIRMED".equals(status) && !"RESOLVED".equals(status) && !"UNCONFIRMED".equals(status)) {
			return ResponseEntity.badRequest().body(Map.of("message", "status는 UNCONFIRMED/CONFIRMED/RESOLVED 중 하나여야 합니다."));
		}

		Integer handledBy = (Integer) session.getAttribute("userId");
		if (handledBy == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요합니다."));
		}

		Event updated = eventService.updateStatus(id, status, memo, handledBy);
		if (updated == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "없는 이벤트입니다."));
		}
		workHistoryService.log(handledBy, "UPDATE_EVENT_STATUS", "event:" + id, status + (memo != null ? " - " + memo : ""));
		return ResponseEntity.ok(updated);
	}

}
