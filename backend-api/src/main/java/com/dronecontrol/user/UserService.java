package com.dronecontrol.user;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {

	@Autowired
	private UserMapper userMapper;

	public enum RegisterResult { OK, DUPLICATE_LOGIN_ID }

	public static class RegisterOutcome {
		public RegisterResult result;
		public User user;
	}

	/** 회원가입. 최초 1명은 SUPER_ADMIN으로, 그 다음부터는 PRE_USER(미승인)로 등록한다. */
	public RegisterOutcome register(RegisterRequest req) {
		RegisterOutcome outcome = new RegisterOutcome();

		if (userMapper.selectByLoginId(req.getLoginId()) != null) {
			outcome.result = RegisterResult.DUPLICATE_LOGIN_ID;
			return outcome;
		}

		User user = new User();
		user.setLoginId(req.getLoginId());
		user.setPasswordEncoded(PasswordUtil.hash(req.getPassword()));
		user.setEmail(req.getEmail());
		user.setPhone(req.getPhone());
		user.setName(req.getName());
		user.setRole(userMapper.countSuperAdmins() == 0 ? "SUPER_ADMIN" : "PRE_USER");

		userMapper.insertUser(user);

		outcome.result = RegisterResult.OK;
		outcome.user = user;
		return outcome;
	}

	/** @return 로그인 성공 시 User, 실패(아이디 없음/비밀번호 틀림) 시 null */
	public User login(LoginRequest req) {
		User user = userMapper.selectByLoginId(req.getLoginId());
		if (user == null) {
			return null;
		}
		if (!PasswordUtil.matches(req.getPassword(), user.getPasswordEncoded())) {
			return null;
		}
		return user;
	}

	public User getById(int id) {
		return userMapper.selectById(id);
	}

	public List<User> listByRole(String role) {
		return userMapper.selectByRole(role);
	}

	public User updateProfile(int id, User patch) {
		User existing = userMapper.selectById(id);
		if (existing == null) {
			return null;
		}
		if (patch.getEmail() != null) existing.setEmail(patch.getEmail());
		if (patch.getPhone() != null) existing.setPhone(patch.getPhone());
		if (patch.getName() != null) existing.setName(patch.getName());

		int result = userMapper.updateProfile(existing);
		return (result > 0) ? existing : null;
	}

	/** PRE_USER -> USER (승인) */
	public User approve(int id) {
		return changeRole(id, "USER");
	}

	/** USER -> PRE_USER (미승인 처리로 되돌림) */
	public User reject(int id) {
		return changeRole(id, "PRE_USER");
	}

	private User changeRole(int id, String role) {
		User existing = userMapper.selectById(id);
		if (existing == null) {
			return null;
		}
		int result = userMapper.updateRole(id, role);
		if (result <= 0) {
			return null;
		}
		existing.setRole(role);
		return existing;
	}

	public boolean delete(int id) {
		return userMapper.deleteUser(id) > 0;
	}

}
