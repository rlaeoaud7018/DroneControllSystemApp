package com.dronecontrol.mobile;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.dronecontrol.mobile.net.ApiClient;

import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {

	private View loginSection;
	private View registerSection;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_login);

		loginSection = findViewById(R.id.loginSection);
		registerSection = findViewById(R.id.registerSection);

		findViewById(R.id.tabLoginBtn).setOnClickListener(v -> {
			loginSection.setVisibility(View.VISIBLE);
			registerSection.setVisibility(View.GONE);
		});

		findViewById(R.id.tabRegisterBtn).setOnClickListener(v -> {
			loginSection.setVisibility(View.GONE);
			registerSection.setVisibility(View.VISIBLE);
		});

		findViewById(R.id.loginSubmitBtn).setOnClickListener(v -> login());
		findViewById(R.id.registerSubmitBtn).setOnClickListener(v -> register());
	}

	private void login() {
		String loginId = ((EditText) findViewById(R.id.loginLoginId)).getText().toString();
		String password = ((EditText) findViewById(R.id.loginPassword)).getText().toString();
		TextView errorView = findViewById(R.id.loginError);
		errorView.setText("");

		String body = "{\"loginId\":\"" + escape(loginId) + "\",\"password\":\"" + escape(password) + "\"}";

		ApiClient.post(ApiClient.BACKEND_BASE + "/api/v1/auth/login", body, new ApiClient.Callback() {
			@Override
			public void onSuccess(String responseBody) {
				goToMain();
			}

			@Override
			public void onError(Exception e) {
				errorView.setText(extractMessage(e, "로그인에 실패했습니다."));
			}
		});
	}

	private void register() {
		String loginId = ((EditText) findViewById(R.id.regLoginId)).getText().toString();
		String password = ((EditText) findViewById(R.id.regPassword)).getText().toString();
		String name = ((EditText) findViewById(R.id.regName)).getText().toString();
		String email = ((EditText) findViewById(R.id.regEmail)).getText().toString();
		String phone = ((EditText) findViewById(R.id.regPhone)).getText().toString();
		TextView errorView = findViewById(R.id.registerError);
		errorView.setText("");

		JSONObject body = new JSONObject();
		try {
			body.put("loginId", loginId);
			body.put("password", password);
			body.put("name", name);
			body.put("email", email);
			body.put("phone", phone);
		} catch (Exception ignored) {
		}

		ApiClient.post(ApiClient.BACKEND_BASE + "/api/v1/users", body.toString(), new ApiClient.Callback() {
			@Override
			public void onSuccess(String responseBody) {
				// 가입 성공 후 같은 자격증명으로 바로 로그인 시도
				((EditText) findViewById(R.id.loginLoginId)).setText(loginId);
				((EditText) findViewById(R.id.loginPassword)).setText(password);
				loginSection.setVisibility(View.VISIBLE);
				registerSection.setVisibility(View.GONE);
				login();
			}

			@Override
			public void onError(Exception e) {
				errorView.setText(extractMessage(e, "가입에 실패했습니다."));
			}
		});
	}

	private void goToMain() {
		startActivity(new Intent(this, MainActivity.class));
		finish();
	}

	private String escape(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private String extractMessage(Exception e, String fallback) {
		if (e instanceof ApiClient.ApiException) {
			try {
				return new JSONObject(((ApiClient.ApiException) e).getResponseBody()).optString("message", fallback);
			} catch (Exception ignored) {
			}
		}
		return fallback;
	}

}
