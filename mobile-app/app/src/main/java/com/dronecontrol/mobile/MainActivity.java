package com.dronecontrol.mobile;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.dronecontrol.mobile.model.Camera;
import com.dronecontrol.mobile.model.User;
import com.dronecontrol.mobile.model.Zone;
import com.dronecontrol.mobile.model.ZoneRow;
import com.dronecontrol.mobile.net.ApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

	private static final int DONG_ID = 1; // 데모용 고정값

	private SwipeRefreshLayout swipeRefresh;
	private ZoneAdapter adapter;
	private User currentUser;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		swipeRefresh = findViewById(R.id.swipeRefresh);
		RecyclerView recyclerView = findViewById(R.id.zoneRecyclerView);
		recyclerView.setLayoutManager(new LinearLayoutManager(this));

		adapter = new ZoneAdapter(row -> {
			Intent intent = new Intent(this, ZoneDetailActivity.class);
			intent.putExtra("zoneId", row.zone.id);
			intent.putExtra("zoneName", row.zone.name);
			if (row.camera != null) {
				intent.putExtra("cameraId", row.camera.id);
			}
			startActivity(intent);
		});
		recyclerView.setAdapter(adapter);

		swipeRefresh.setOnRefreshListener(this::loadZones);

		findViewById(R.id.mapBtn).setOnClickListener(v -> startActivity(new Intent(this, MapActivity.class)));

		findViewById(R.id.eventsBtn).setOnClickListener(v -> {
			if (currentUser == null) return;
			Intent intent = new Intent(this, EventListActivity.class);
			intent.putExtra("isAdmin", currentUser.isAdmin());
			startActivity(intent);
		});

		findViewById(R.id.logoutBtn).setOnClickListener(v -> logout());
	}

	@Override
	protected void onResume() {
		super.onResume();
		checkAuth();
	}

	private void checkAuth() {
		ApiClient.get(ApiClient.BACKEND_BASE + "/api/v1/users/me", new ApiClient.Callback() {
			@Override
			public void onSuccess(String responseBody) {
				try {
					currentUser = new User(new JSONObject(responseBody));
				} catch (Exception e) {
					goToLogin();
					return;
				}

				if ("PRE_USER".equals(currentUser.role)) {
					startActivity(new Intent(MainActivity.this, PendingApprovalActivity.class));
					finish();
					return;
				}

				loadZones();
			}

			@Override
			public void onError(Exception e) {
				goToLogin();
			}
		});
	}

	private void logout() {
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
	}

	private void goToLogin() {
		startActivity(new Intent(this, LoginActivity.class));
		finish();
	}

	private void loadZones() {
		swipeRefresh.setRefreshing(true);

		String url = ApiClient.BACKEND_BASE + "/api/v1/zones?dongId=" + DONG_ID;
		ApiClient.get(url, new ApiClient.Callback() {
			@Override
			public void onSuccess(String responseBody) {
				try {
					JSONArray zonesJson = new JSONArray(responseBody);
					List<Zone> zones = new ArrayList<>();
					for (int i = 0; i < zonesJson.length(); i++) {
						zones.add(new Zone(zonesJson.getJSONObject(i)));
					}
					loadCamerasForZones(zones);
				} catch (Exception e) {
					onError(e);
				}
			}

			@Override
			public void onError(Exception e) {
				swipeRefresh.setRefreshing(false);
				Toast.makeText(MainActivity.this, "구역 목록을 불러오지 못했습니다: " + e.getMessage(), Toast.LENGTH_LONG).show();
			}
		});
	}

	private void loadCamerasForZones(List<Zone> zones) {
		List<ZoneRow> rows = new ArrayList<>();
		loadCamerasForZones(zones, 0, rows);
	}

	private void loadCamerasForZones(List<Zone> zones, int index, List<ZoneRow> rows) {
		if (index >= zones.size()) {
			android.util.Log.d("MainActivity", "setRows size=" + rows.size());
			adapter.setRows(rows);
			swipeRefresh.setRefreshing(false);
			return;
		}

		Zone zone = zones.get(index);
		String url = ApiClient.BACKEND_BASE + "/api/v1/cameras?zoneId=" + zone.id;
		ApiClient.get(url, new ApiClient.Callback() {
			@Override
			public void onSuccess(String responseBody) {
				try {
					JSONArray camerasJson = new JSONArray(responseBody);
					Camera camera = (camerasJson.length() > 0) ? new Camera((JSONObject) camerasJson.get(0)) : null;
					rows.add(new ZoneRow(zone, camera));
				} catch (Exception ignored) {
					rows.add(new ZoneRow(zone, null));
				}
				loadCamerasForZones(zones, index + 1, rows);
			}

			@Override
			public void onError(Exception e) {
				rows.add(new ZoneRow(zone, null));
				loadCamerasForZones(zones, index + 1, rows);
			}
		});
	}

}
