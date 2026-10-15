package com.dronecontrol.user;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 간단한 SHA-256 해시. 보안은 지금 단계에서 우선순위가 아니라서(발표/시연용 프로젝트),
 * bcrypt 같은 전용 라이브러리를 추가하지 않고 최소한의 평문 저장 방지만 함.
 */
public class PasswordUtil {

	public static String hash(String plainPassword) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashed = digest.digest(plainPassword.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (byte b : hashed) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	public static boolean matches(String plainPassword, String hashed) {
		return hash(plainPassword).equals(hashed);
	}

}
