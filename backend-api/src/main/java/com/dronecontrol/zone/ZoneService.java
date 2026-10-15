package com.dronecontrol.zone;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ZoneService {

	@Autowired
	private ZoneMapper zoneMapper;

	public Zone createZone(Zone zone) {
		int result = zoneMapper.insertZone(zone);
		return (result > 0) ? zone : null;
	}

	public List<Zone> listZonesByDongId(int dongId) {
		return zoneMapper.selectZonesByDongId(dongId);
	}

	public Zone getZone(int id) {
		return zoneMapper.selectZoneById(id);
	}

	public Zone updateZone(int id, Zone patch) {
		Zone existing = zoneMapper.selectZoneById(id);
		if (existing == null) {
			return null;
		}

		if (patch.getName() != null) {
			existing.setName(patch.getName());
		}
		if (patch.getAreaM2() != null) {
			existing.setAreaM2(patch.getAreaM2());
		}
		if (patch.getGeometry() != null) {
			existing.setGeometry(patch.getGeometry());
		}

		int result = zoneMapper.updateZone(existing);
		return (result > 0) ? existing : null;
	}

	/** 반대 방향은 자동으로 함께 등록한다 (N<->S, E<->W). 자기 자신이면 null. */
	public List<ZoneNeighbor> setNeighbor(int zoneId, ZoneNeighbor neighbor) {
		if (zoneId == neighbor.getNeighborId()) {
			return null;
		}

		Zone target = zoneMapper.selectZoneById(neighbor.getNeighborId());
		if (target == null) {
			return null;
		}

		String opposite = oppositeDirection(neighbor.getDirection());
		if (opposite == null) {
			return null;
		}

		zoneMapper.upsertNeighbor(zoneId, neighbor.getDirection(), neighbor.getNeighborId());
		zoneMapper.upsertNeighbor(neighbor.getNeighborId(), opposite, zoneId);

		return zoneMapper.selectNeighbors(zoneId);
	}

	public List<ZoneNeighbor> getNeighbors(int zoneId) {
		return zoneMapper.selectNeighbors(zoneId);
	}

	private String oppositeDirection(String direction) {
		if (direction == null) {
			return null;
		}
		switch (direction) {
			case "N": return "S";
			case "S": return "N";
			case "E": return "W";
			case "W": return "E";
			default: return null;
		}
	}

}
