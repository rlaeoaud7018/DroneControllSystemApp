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

	/** 비밀번호 재설정(acman-004). 본인은 currentPassword 필요, 관리자가 타인 비밀번호를 초기화할 땐 불필요. */
	@PatchMapping("/{id}/password")
	public ResponseEntity<?> changePassword(@PathVariable int id, @RequestBody Map<String, String> body, HttpSession session) {
		Integer sessionUserId = (Integer) session.getAttribute("userId");
		if (sessionUserId == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요합니다."));
		}
		boolean isSelf = sessionUserId == id;
		if (!isSelf && !isAdmin(session)) {
			return forbidden();
		}
		String newPassword = body.get("newPassword");
		if (newPassword == null || newPassword.isBlank()) {
			return ResponseEntity.badRequest().body(Map.of("message", "새 비밀번호를 입력해주세요."));
		}
		boolean requireCurrentCheck = isSelf;
		UserService.PasswordChangeResult result = userService.changePassword(id, body.get("currentPassword"), newPassword, requireCurrentCheck);
		if (result == UserService.PasswordChangeResult.NOT_FOUND) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "없는 회원입니다."));
		}
		if (result == UserService.PasswordChangeResult.WRONG_CURRENT_PASSWORD) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "현재 비밀번호가 일치하지 않습니다."));
		}
		if (!isSelf) {
			logAction(session, "RESET_PASSWORD", "user:" + id, id + "번 회원 비밀번호 초기화");
		}
		return ResponseEntity.ok(Map.of("message", "비밀번호가 변경되었습니다."));
	}

	/** 관리자 간 관리(acman-005~006): SUPER_ADMIN이 USER<->ADMIN 권한을 전환. */
	@PatchMapping("/{id}/role")
	public ResponseEntity<?> changeRole(@PathVariable int id, @RequestBody Map<String, String> body, HttpSession session) {
		if (!isSuperAdmin(session)) {
			return forbidden();
		}
		String role = body.get("role");
		if (!"ADMIN".equals(role) && !"USER".equals(role)) {
			return ResponseEntity.badRequest().body(Map.of("message", "role은 ADMIN 또는 USER여야 합니다."));
		}
		UserService.RoleChangeResult result = userService.changeAdminRole(id, role);
		if (result == UserService.RoleChangeResult.NOT_FOUND) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "없는 회원입니다."));
		}
		if (result == UserService.RoleChangeResult.NOT_ELIGIBLE) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "최고관리자이거나 승인 대기 중인 회원은 이 API로 권한을 바꿀 수 없습니다."));
		}
		logAction(session, "CHANGE_ROLE", "user:" + id, id + "번 회원 권한을 " + role + "로 변경");
		return ResponseEntity.ok(Map.of("message", "권한이 변경되었습니다.", "role", role));
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

	private boolean isSuperAdmin(HttpSession session) {
		return "SUPER_ADMIN".equals(session.getAttribute("userRole"));
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
