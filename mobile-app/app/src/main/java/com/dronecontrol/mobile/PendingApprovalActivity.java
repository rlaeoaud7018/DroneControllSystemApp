package com.dronecontrol.mobile;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.dronecontrol.mobile.net.ApiClient;

public class PendingApprovalActivity extends AppCompatActivity {

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_pending_approval);

		findViewById(R.id.logoutBtn).setOnClickListener(v -> {
			ApiClient.post(ApiClient.BACKEND_BASE + "/api/v1/auth/logout", null, new ApiClient.Callback() {
				@Override
				public void onSuccess(String responseBody) {
					goToLogin();
				}

				@Override
				public void onError(Exception e) {
					goToLogin();
				}
			});
		});
	}

	private void goToLogin() {
		Intent intent = new Intent(this, LoginActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(intent);
		finish();
	}

}
