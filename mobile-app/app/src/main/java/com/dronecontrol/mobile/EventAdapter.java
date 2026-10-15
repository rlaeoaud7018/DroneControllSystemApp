package com.dronecontrol.mobile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dronecontrol.mobile.model.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.ViewHolder> {

	public interface OnStatusChangeListener {
		void onChange(Event event, String newStatus, String memo);
	}

	private static final Map<Integer, String> LEVEL_LABEL = Map.of(1, "주의", 2, "위험");

	private final List<Event> events = new ArrayList<>();
	private final Map<Integer, String> zoneNames;
	private final boolean isAdmin;
	private final OnStatusChangeListener listener;

	public EventAdapter(Map<Integer, String> zoneNames, boolean isAdmin, OnStatusChangeListener listener) {
		this.zoneNames = zoneNames;
		this.isAdmin = isAdmin;
		this.listener = listener;
	}

	public void setEvents(List<Event> newEvents) {
		events.clear();
		events.addAll(newEvents);
		notifyDataSetChanged();
	}

	@NonNull
	@Override
	public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_event, parent, false);
		return new ViewHolder(view);
	}

	@Override
	public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
		Event event = events.get(position);
		String zoneName = zoneNames.getOrDefault(event.zoneId, "구역 " + event.zoneId);
		String levelLabel = LEVEL_LABEL.getOrDefault(event.levelNo, String.valueOf(event.levelNo));

		holder.zoneLevel.setText(zoneName + " · " + levelLabel + "단계");
		holder.status.setText(event.status);

		String meta = "발생: " + event.occurredAt + " · 밀집도 " + event.densityAtEvent + " (기준 " + event.thresholdDensity + ")";
		if (event.memo != null) {
			meta += " · 메모: " + event.memo;
		}
		holder.meta.setText(meta);

		boolean showActions = isAdmin && !"RESOLVED".equals(event.status);
		holder.actions.setVisibility(showActions ? View.VISIBLE : View.GONE);

		if (showActions) {
			holder.confirmBtn.setVisibility("UNCONFIRMED".equals(event.status) ? View.VISIBLE : View.GONE);
			holder.confirmBtn.setOnClickListener(v ->
					listener.onChange(event, "CONFIRMED", holder.memoInput.getText().toString()));
			holder.resolveBtn.setOnClickListener(v ->
					listener.onChange(event, "RESOLVED", holder.memoInput.getText().toString()));
		}
	}

	@Override
	public int getItemCount() {
		return events.size();
	}

	static class ViewHolder extends RecyclerView.ViewHolder {
		final TextView zoneLevel;
		final TextView status;
		final TextView meta;
		final View actions;
		final EditText memoInput;
		final Button confirmBtn;
		final Button resolveBtn;

		ViewHolder(View itemView) {
			super(itemView);
			zoneLevel = itemView.findViewById(R.id.eventZoneLevel);
			status = itemView.findViewById(R.id.eventStatus);
			meta = itemView.findViewById(R.id.eventMeta);
			actions = itemView.findViewById(R.id.eventActions);
			memoInput = itemView.findViewById(R.id.eventMemoInput);
			confirmBtn = itemView.findViewById(R.id.confirmBtn);
			resolveBtn = itemView.findViewById(R.id.resolveBtn);
		}
	}

}
