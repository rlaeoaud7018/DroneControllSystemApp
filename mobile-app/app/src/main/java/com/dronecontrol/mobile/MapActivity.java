package com.dronecontrol.mobile;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.dronecontrol.mobile.model.Zone;
import com.dronecontrol.mobile.net.ApiClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MapActivity extends FragmentActivity implements OnMapReadyCallback {

	private static final int DONG_ID = 1;
	private static final long STATS_WINDOW_MINUTES = 10;

	private final SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.KOREA);

	private GoogleMap googleMap;
	private final Map<Integer, Integer> zoneIdToCameraId = new HashMap<>();

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_map);

		SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
		if (mapFragment != null) {
			mapFragment.getMapAsync(this);
		}
	}

	@Override
	public void onMapReady(GoogleMap map) {
		googleMap = map;
		loadZones();
	}

	private void loadZones() {
		ApiClient.get(ApiClient.BACKEND_BASE + "/api/v1/zones?dongId=" + DONG_ID, new ApiClient.Callback() {
			@Override
			public void onSuccess(String responseBody) {
				try {
					JSONArray zonesJson = new JSONArray(responseBody);
					List<Zone> zones = new ArrayList<>();
					for (int i = 0; i < zonesJson.length(); i++) {
						zones.add(new Zone(zonesJson.getJSONObject(i)));
					}
					placeMarkers(zones, 0);
				} catch (Exception ignored) {
				}
			}

			@Override
			public void onError(Exception e) {
				Toast.makeText(MapActivity.this, "구역 목록을 불러오지 못했습니다.", Toast.LENGTH_SHORT).show();
			}
		});
	}

	private void placeMarkers(List<Zone> zones, int index) {
		if (index >= zones.size()) {
			return;
		}

		Zone zone = zones.get(index);
		loadCameraForZone(zone, () -> loadLatestCount(zone, (count) -> {
			addMarker(zone, count);
			placeMarkers(zones, index + 1);
		}));
	}

	private interface Continuation {
		void run();
	}

	private void loadCameraForZone(Zone zone, Continuation next) {
		ApiClient.get(ApiClient.BACKEND_BASE + "/api/v1/cameras?zoneId=" + zone.id, new ApiClient.Callback() {
			@Override
			public void onSuccess(String responseBody) {
				try {
					JSONArray camerasJson = new JSONArray(responseBody);
					if (camerasJson.length() > 0) {
						zoneIdToCameraId.put(zone.id, camerasJson.getJSONObject(0).getInt("id"));
					}
				} catch (Exception ignored) {
				}
				next.run();
			}

			@Override
			public void onError(Exception e) {
				next.run();
			}
		});
	}

	private interface CountCallback {
		void onCount(int count);
	}

	private void loadLatestCount(Zone zone, CountCallback callback) {
		Date to = new Date();
		Date from = new Date(to.getTime() - STATS_WINDOW_MINUTES * 60 * 1000);
		String url = ApiClient.BACKEND_BASE + "/api/v1/zones/" + zone.id + "/stats"
				+ "?from=" + isoFormat.format(from) + "&to=" + isoFormat.format(to);

		ApiClient.get(url, new ApiClient.Callback() {
			@Override
			public void onSuccess(String responseBody) {
				int count = 0;
				try {
					JSONArray stats = new JSONArray(responseBody);
					if (stats.length() > 0) {
						count = (int) Math.round(((JSONObject) stats.get(stats.length() - 1)).getDouble("countAvg"));
					}
				} catch (Exception ignored) {
				}
				callback.onCount(count);
			}

			@Override
			public void onError(Exception e) {
				callback.onCount(0);
			}
		});
	}

	private void addMarker(Zone zone, int count) {
		if (zone.geometry == null) {
			return;
		}

		try {
			JSONObject geo = new JSONObject(zone.geometry);
			LatLng position = new LatLng(geo.getDouble("lat"), geo.getDouble("lng"));

			Marker marker = googleMap.addMarker(new MarkerOptions()
					.position(position)
					.title(zone.name)
					.snippet("현재 인원: " + count + "명"));

			if (marker != null) {
				marker.setTag(zone.id);
			}

			googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 13f));

			googleMap.setOnMarkerClickListener(m -> {
				Integer zoneId = (Integer) m.getTag();
				if (zoneId != null) {
					openZone(zoneId, m.getTitle());
				}
				return false; // 기본 동작(인포윈도우 표시)도 유지
			});
		} catch (Exception ignored) {
		}
	}

	private void openZone(int zoneId, String zoneName) {
		Intent intent = new Intent(this, ZoneDetailActivity.class);
		intent.putExtra("zoneId", zoneId);
		intent.putExtra("zoneName", zoneName);
		Integer cameraId = zoneIdToCameraId.get(zoneId);
		if (cameraId != null) {
			intent.putExtra("cameraId", (int) cameraId);
		}
		startActivity(intent);
	}

}
