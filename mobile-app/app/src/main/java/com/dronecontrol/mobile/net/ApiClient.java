package com.dronecontrol.mobile.net;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * adb reverse tcp:8080 tcp:8080 / adb reverse tcp:5050 tcp:5050 를 먼저 실행해둬야 한다.
 * (10.0.2.2로 직접 접속하면 Windows 방화벽이 "공용 네트워크" 인바운드로 보고 막는 경우가 있어서,
 *  adb reverse로 에뮬레이터의 localhost를 PC 포트에 직접 터널링하는 방식을 씀)
 * 실제 기기로 테스트할 때는 PC의 실제 IP로 바꾸거나 USB로 adb reverse를 똑같이 걸어주면 된다.
 */
public class ApiClient {

	public static final String BACKEND_BASE = "http://localhost:8080/backend-api";
	public static final String ANALYSIS_BASE = "http://localhost:5050";

	private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
	private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

	static {
		// 로그인 세션 쿠키(JSESSIONID)를 요청 간에 자동으로 유지하기 위함
		CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
	}

	public interface Callback {
		void onSuccess(String responseBody);
		void onError(Exception e);
	}

	/** HTTP 상태 코드를 그대로 담아 돌려주는 예외. e.getStatusCode()로 401/403 등을 구분할 수 있다. */
	public static class ApiException extends IOException {
		private final int statusCode;
		private final String responseBody;

		public ApiException(int statusCode, String responseBody) {
			super("HTTP " + statusCode + ": " + responseBody);
			this.statusCode = statusCode;
			this.responseBody = responseBody;
		}

		public int getStatusCode() {
			return statusCode;
		}

		public String getResponseBody() {
			return responseBody;
		}
	}

	public static void get(String url, Callback callback) {
		request(url, "GET", null, callback);
	}

	public static void post(String url, String jsonBody, Callback callback) {
		request(url, "POST", jsonBody, callback);
	}

	public static void patch(String url, String jsonBody, Callback callback) {
		request(url, "PATCH", jsonBody, callback);
	}

	private static void request(String url, String method, String body, Callback callback) {
		Log.d("ApiClient", method + " " + url + " 시작");
		EXECUTOR.execute(() -> {
			try {
				String result = execute(url, method, body);
				Log.d("ApiClient", method + " " + url + " 성공: " + result);
				MAIN_HANDLER.post(() -> callback.onSuccess(result));
			} catch (Exception e) {
				Log.e("ApiClient", method + " " + url + " 실패", e);
				MAIN_HANDLER.post(() -> callback.onError(e));
			}
		});
	}

	private static String execute(String urlStr, String method, String body) throws IOException {
		HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
		conn.setRequestMethod(method);
		conn.setConnectTimeout(5000);
		conn.setReadTimeout(5000);

		if (body != null) {
			conn.setDoOutput(true);
			conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
			try (OutputStream os = conn.getOutputStream()) {
				os.write(body.getBytes(StandardCharsets.UTF_8));
			}
		}

		int code = conn.getResponseCode();
		InputStream stream = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
		String responseBody = (stream != null) ? readAll(stream) : "";

		if (code < 200 || code >= 300) {
			throw new ApiException(code, responseBody);
		}

		return responseBody;
	}

	private static String readAll(InputStream is) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buffer = new byte[4096];
		int n;
		while ((n = is.read(buffer)) != -1) {
			baos.write(buffer, 0, n);
		}
		return baos.toString("UTF-8");
	}

}
