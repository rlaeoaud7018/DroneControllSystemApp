package com.dronecontrol.mobile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dronecontrol.mobile.model.ZoneRow;

import java.util.ArrayList;
import java.util.List;

public class ZoneAdapter extends RecyclerView.Adapter<ZoneAdapter.ViewHolder> {

	public interface OnRowClickListener {
		void onClick(ZoneRow row);
	}

	private final List<ZoneRow> rows = new ArrayList<>();
	private final OnRowClickListener listener;

	public ZoneAdapter(OnRowClickListener listener) {
		this.listener = listener;
	}

	public void setRows(List<ZoneRow> newRows) {
		rows.clear();
		rows.addAll(newRows);
		notifyDataSetChanged();
	}

	@NonNull
	@Override
	public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_zone, parent, false);
		return new ViewHolder(view);
	}

	@Override
	public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
		ZoneRow row = rows.get(position);

		holder.zoneName.setText(row.zone.name);

		if (row.camera != null) {
			holder.cameraName.setText(row.camera.name);
			holder.statusBadge.setText(row.camera.status);
		} else {
			holder.cameraName.setText("캠 없음");
			holder.statusBadge.setText("");
		}

		holder.itemView.setOnClickListener(v -> listener.onClick(row));
	}

	@Override
	public int getItemCount() {
		return rows.size();
	}

	static class ViewHolder extends RecyclerView.ViewHolder {
		final TextView zoneName;
		final TextView cameraName;
		final TextView statusBadge;

		ViewHolder(View itemView) {
			super(itemView);
			zoneName = itemView.findViewById(R.id.zoneName);
			cameraName = itemView.findViewById(R.id.cameraName);
			statusBadge = itemView.findViewById(R.id.statusBadge);
		}
	}

}
