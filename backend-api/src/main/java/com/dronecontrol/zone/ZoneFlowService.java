package com.dronecontrol.zone;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.dronecontrol.stats.ZoneMinuteStat;

/** 구역 간 이동량 집계(stats-006). zone_stats_minute의 outflow_n/e/s/w를 인접 구역(zone_neighbors)으로 매핑해 zone_flows에 반영한다. */
@Service
public class ZoneFlowService {

	@Autowired
	private ZoneMapper zoneMapper;

	@Autowired
	private ZoneFlowMapper zoneFlowMapper;

	/** 분단위 통계가 갱신될 때마다 호출 — 그 구역의 현재까지 누적된 outflow 값을 인접 구역으로의 흐름으로 반영한다. */
	public void recordFlows(int zoneId, String minuteAt, ZoneMinuteStat stat) {
		List<ZoneNeighbor> neighbors = zoneMapper.selectNeighbors(zoneId);
		if (neighbors == null || neighbors.isEmpty()) {
			return;
		}
		for (ZoneNeighbor neighbor : neighbors) {
			int outflow = outflowFor(neighbor.getDirection(), stat);
			if (outflow <= 0) {
				continue;
			}
			zoneFlowMapper.upsertFlow(zoneId, neighbor.getNeighborId(), minuteAt, outflow);
		}
	}

	private int outflowFor(String direction, ZoneMinuteStat stat) {
		switch (direction) {
			case "N": return stat.getOutflowN();
			case "E": return stat.getOutflowE();
			case "S": return stat.getOutflowS();
			case "W": return stat.getOutflowW();
			default: return 0;
		}
	}

	public List<ZoneFlow> getFlowsByDong(int dongId, String from, String to) {
		return zoneFlowMapper.selectFlowsByDong(dongId, from, to);
	}

}
