package com.dronecontrol.mobile;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.dronecontrol.mobile.model.Camera;
import com.dronecontrol.mobile.model.ZoneStat;
import com.dronecontrol.mobile.net.ApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ZoneDetailActivity extends AppCompatActivity {

	private static final long STATS_WINDOW_MINUTES = 10;
	private static final long POLL_INTERVAL_MS = 5000;

	private final SimpleDateFormat isoFormat =
			new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.KOREA);

	private int zoneId;
	private String zoneName;
	private int cameraId = -1; // -1 이면 캠 없음
	private Camera currentCamera;

	private TextView detailTitle;
	private TextView statCount;
	private TextView statDensity;
	private TextView statDwell;
	private TextView cameraStatusBadge;
	private Button toggleButton;
	private WebView liveView;

	private final Handler handler = new Handler(Looper.getMainLooper());
	private final Runnable pollRunnable = this::refresh;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_zone_detail);

		zoneId = getIntent().getIntExtra("zoneId", -1);
		zoneName = getIntent().getStringExtra("zoneName");
		cameraId = getIntent().getIntExtra("cameraId", -1);

		detailTitle = findViewById(R.id.detailTitle);
		statCount = findViewById(R.id.statCount);
		statDensity = findViewById(R.id.statDensity);
		statDwell = findViewById(R.id.statDwell);
		cameraStatusBadge = findViewById(R.id.cameraStatusBadge);
		toggleButton = findViewById(R.id.toggleButton);
		liveView = findViewById(R.id.liveView);
		liveView.getSettings().setJavaScriptEnabled(false);

		detailTitle.setText(zoneName);

		if (cameraId < 0) {
			cameraStatusBadge.setText("이 구역에는 캠이 없습니다");
			toggleButton.setVisibility(View.GONE);
		} else {
			toggleButton.setOnClickListener(v -> toggleCamera());
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		refresh();
	}

	@Override
	protected void onPause() {
		super.onPause();
		handler.removeCallbacks(pollRunnable);
	}

	private void refresh() {
		refreshStats();
		if (cameraId >= 0) {
			refreshCamera();
		}
		handler.removeCallbacks(pollRunnable);
		handler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
	}

	private void refreshStats() {
		Date to = new Date();
		Date from = new Date(to.getTime() - STATS_WINDOW_MINUTES * 60 * 1000);

		String url = ApiClient.BACKEND_BASE + "/api/v1/zones/" + zoneId + "/stats"
				+ "?from=" + isoFormat.format(from) + "&to=" + isoFormat.format(to);

		ApiClient.get(url, new ApiClient.Callback() {
			@Override
			public void onSuccess(String responseBody) {
				try {
					JSONArray stats = new JSONArray(responseBody);
					if (stats.length() == 0) {
						statCount.setText("-");
						statDensity.setText("-");
						statDwell.setText("-");
						return;
					}
					ZoneStat latest = new ZoneStat((JSONObject) stats.get(stats.length() - 1));
					statCount.setText(String.valueOf(Math.round(latest.countAvg)));
					statDensity.setText(String.format(Locale.KOREA, "%.3f", latest.density));
					statDwell.setText(String.valueOf(latest.dwellAvgSec));
				} catch (Exception ignored) {
				}
			}

			@Override
			public void onError(Exception e) {
				// 통계가 아직 없을 수 있음 (캠 미가동 등) - 조용히 무시
			}
		});
	}

	private void refreshCamera() {
		String url = ApiClient.BACKEND_BASE + "/api/v1/cameras/" + cameraId;

		ApiClient.get(url, new ApiClient.Callback() {
			@Override
			public void onSuccess(String responseBody) {
				try {
					currentCamera = new Camera(new JSONObject(responseBody));
					renderCamera();
				} catch (Exception ignored) {
				}
			}

			@Override
			public void onError(Exception e) {
			}
		});
	}

	private void renderCamera() {
		cameraStatusBadge.setText(currentCamera.name + " · " + currentCamera.status);

		boolean running = "RUNNING".equals(currentCamera.status);
		boolean transitioning = "PREPARING".equals(currentCamera.status);

		toggleButton.setEnabled(!transitioning);
		toggleButton.setText(running || transitioning ? "정지" : "가동");

		if (running) {
			String liveUrl = ApiClient.ANALYSIS_BASE + "/analyzer/v1/cameras/" + cameraId + "/live";
			String html = "<html><body style='margin:0'><img src='" + liveUrl + "' style='width:100%;' /></body></html>";
			liveView.loadDataWithBaseURL(ApiClient.ANALYSIS_BASE, html, "text/html", "UTF-8", null);
			liveView.setVisibility(View.VISIBLE);
		} else {
			liveView.setVisibility(View.GONE);
		}
	}

	private void toggleCamera() {
		if (currentCamera == null) {
			return;
		}

		boolean currentlyOnOrStarting = "RUNNING".equals(currentCamera.status) || "PREPARING".equals(currentCamera.status);
		String targetState = currentlyOnOrStarting ? "STOPPED" : "RUNNING";

		String url = ApiClient.BACKEND_BASE + "/api/v1/cameras/" + cameraId + "/state";
		String body = "{\"state\":\"" + targetState + "\"}";

		ApiClient.patch(url, body, new ApiClient.Callback() {
			@Override
			public void onSuccess(String responseBody) {
				try {
					currentCamera = new Camera(new JSONObject(responseBody));
					renderCamera();
					if ("RUNNING".equals(targetState)) {
						handler.postDelayed(() -> refreshCamera(), 1500);
					}
				} catch (Exception ignored) {
				}
			}

			@Override
			public void onError(Exception e) {
				Toast.makeText(ZoneDetailActivity.this, "요청 실패: " + e.getMessage(), Toast.LENGTH_LONG).show();
			}
		});
	}

}
