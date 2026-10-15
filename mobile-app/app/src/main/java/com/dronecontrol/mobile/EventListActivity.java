package com.dronecontrol.mobile;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dronecontrol.mobile.model.Event;
import com.dronecontrol.mobile.net.ApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventListActivity extends AppCompatActivity {

	private static final int DONG_ID = 1;

	private EventAdapter adapter;
	private final Map<Integer, String> zoneNames = new HashMap<>();

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_event_list);

		boolean isAdmin = getIntent().getBooleanExtra("isAdmin", false);

		RecyclerView recyclerView = findViewById(R.id.eventRecyclerView);
		recyclerView.setLayoutManager(new LinearLayoutManager(this));

		adapter = new EventAdapter(zoneNames, isAdmin, (event, newStatus, memo) -> updateEventStatus(event.id, newStatus, memo));
		recyclerView.setAdapter(adapter);

		loadZoneNames();
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadEvents();
	}

	private void loadZoneNames() {
		ApiClient.get(ApiClient.BACKEND_BASE + "/api/v1/zones?dongId=" + DONG_ID, new ApiClient.Callback() {
			@Override
			public void onSuccess(String responseBody) {
				try {
					JSONArray zonesJson = new JSONArray(responseBody);
					for (int i = 0; i < zonesJson.length(); i++) {
						JSONObject zone = zonesJson.getJSONObject(i);
						zoneNames.put(zone.getInt("id"), zone.getString("name"));
					}
				} catch (Exception ignored) {
				}
				loadEvents();
			}

			@Override
			public void onError(Exception e) {
				loadEvents();
			}
		});
	}

	private void loadEvents() {
		ApiClient.get(ApiClient.BACKEND_BASE + "/api/v1/events", new ApiClient.Callback() {
			@Override
			public void onSuccess(String responseBody) {
				try {
					JSONArray eventsJson = new JSONArray(responseBody);
					List<Event> events = new ArrayList<>();
					for (int i = 0; i < eventsJson.length(); i++) {
						events.add(new Event(eventsJson.getJSONObject(i)));
					}
					adapter.setEvents(events);
				} catch (Exception ignored) {
				}
			}

			@Override
			public void onError(Exception e) {
				Toast.makeText(EventListActivity.this, "이벤트 목록을 불러오지 못했습니다.", Toast.LENGTH_SHORT).show();
			}
		});
	}

	private void updateEventStatus(int eventId, String status, String memo) {
		JSONObject body = new JSONObject();
		try {
			body.put("status", status);
			body.put("memo", memo == null || memo.isEmpty() ? JSONObject.NULL : memo);
		} catch (Exception ignored) {
		}

		ApiClient.patch(ApiClient.BACKEND_BASE + "/api/v1/events/" + eventId, body.toString(), new ApiClient.Callback() {
			@Override
			public void onSuccess(String responseBody) {
				loadEvents();
			}

			@Override
			public void onError(Exception e) {
				Toast.makeText(EventListActivity.this, "처리에 실패했습니다.", Toast.LENGTH_SHORT).show();
			}
		});
	}

}
