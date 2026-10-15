package com.dronecontrol.user;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dronecontrol.history.WorkHistoryService;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

	@Autowired
	private UserService userService;

	@Autowired
	private WorkHistoryService workHistoryService;

	@PostMapping
	public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
		UserService.RegisterOutcome outcome = userService.register(req);

		if (outcome.result == UserService.RegisterResult.DUPLICATE_LOGIN_ID) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "이미 사용 중인 아이디입니다."));
		}

		return ResponseEntity.status(HttpStatus.CREATED).body(outcome.user);
	}

	@GetMapping("/me")
	public ResponseEntity<?> me(HttpSession session) {
		Integer userId = (Integer) session.getAttribute("userId");
		if (userId == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요합니다."));
		}
		User user = userService.getById(userId);
		if (user == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요합니다."));
		}
		return ResponseEntity.ok(user);
	}

	@GetMapping
	public ResponseEntity<?> list(@RequestParam String role, HttpSession session) {
		if (!isAdmin(session)) {
			return forbidden();
		}
		List<User> users = userService.listByRole(role);
		return ResponseEntity.ok(users);
	}

	@PatchMapping("/{id}")
	public ResponseEntity<?> updateProfile(@PathVariable int id, @RequestBody User patch, HttpSession session) {
		if (!isSelfOrAdmin(session, id)) {
			return forbidden();
		}
		User updated = userService.updateProfile(id, patch);
		if (updated == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "없는 회원입니다."));
		}
		return ResponseEntity.ok(updated);
	}

	@PatchMapping("/{id}/approve")
	public ResponseEntity<?> approve(@PathVariable int id, HttpSession session) {
		if (!isAdmin(session)) {
			return forbidden();
		}
		User updated = userService.approve(id);
		if (updated == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "없는 회원입니다."));
		}
		logAction(session, "APPROVE_USER", "user:" + id, updated.getLoginId() + " 승인");
		return ResponseEntity.ok(updated);
	}

	@PatchMapping("/{id}/reject")
	public ResponseEntity<?> reject(@PathVariable int id, HttpSession session) {
		if (!isAdmin(session)) {
			return forbidden();
		}
		User updated = userService.reject(id);
		if (updated == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "없는 회원입니다."));
		}
		logAction(session, "REJECT_USER", "user:" + id, updated.getLoginId() + " 미승인 처리");
		return ResponseEntity.ok(updated);
	}

	private void logAction(HttpSession session, String actionType, String target, String detail) {
		Integer adminId = (Integer) session.getAttribute("userId");
		if (adminId != null) {
			workHistoryService.log(adminId, actionType, target, detail);
		}
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<?> delete(@PathVariable int id, HttpSession session) {
		if (!isSelfOrAdmin(session, id)) {
			return forbidden();
		}
		boolean deleted = userService.delete(id);
		if (!deleted) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "없는 회원입니다."));
		}
		return ResponseEntity.noContent().build();
	}

	private boolean isAdmin(HttpSession session) {
		String role = (String) session.getAttribute("userRole");
		return "ADMIN".equals(role) || "SUPER_ADMIN".equals(role);
	}

	private boolean isSelfOrAdmin(HttpSession session, int targetUserId) {
		Integer sessionUserId = (Integer) session.getAttribute("userId");
		if (sessionUserId == null) {
			return false;
		}
		return sessionUserId == targetUserId || isAdmin(session);
	}

	private ResponseEntity<?> forbidden() {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "권한이 없습니다."));
	}

}
