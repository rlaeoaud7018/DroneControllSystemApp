package com.dronecontrol.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EventService {

	private static final DateTimeFormatter MYSQL_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	// 관리자가 구역별로 설정하지 않았을 때 쓰는 기본 임계치 (web-dashboard에서 쓰던 값과 동일)
	private static final BigDecimal DEFAULT_CAUTION = new BigDecimal("0.01");
	private static final BigDecimal DEFAULT_DANGER = new BigDecimal("0.02");

	@Autowired
	private EventMapper eventMapper;

	/** 통계 집계 직후 호출. 밀집도가 임계치를 넘었고 이미 열려있는 이벤트가 없으면 새 이벤트를 만든다. */
	public void checkAndRecord(int zoneId, Integer cameraId, BigDecimal density) {
		List<EventThreshold> thresholds = getThresholds(zoneId);

		int crossedLevel = 0;
		BigDecimal crossedThreshold = null;
		for (EventThreshold t : thresholds) {
			if (density.compareTo(t.getDensityThreshold()) >= 0 && t.getLevelNo() > crossedLevel) {
				crossedLevel = t.getLevelNo();
				crossedThreshold = t.getDensityThreshold();
			}
		}

		if (crossedLevel == 0) {
			return;
		}

		if (eventMapper.selectOpenEventForZone(zoneId) != null) {
			return; // 이미 처리 안 된 이벤트가 있으면 중복 생성하지 않음
		}

		Event event = new Event();
		event.setZoneId(zoneId);
		event.setCameraId(cameraId);
		event.setLevelNo(crossedLevel);
		event.setOccurredAt(LocalDateTime.now().format(MYSQL_DATETIME));
		event.setDensityAtEvent(density);
		event.setThresholdDensity(crossedThreshold);

		eventMapper.insertEvent(event);
	}

	public List<Event> listEvents(Integer zoneId, String status) {
		return eventMapper.selectEvents(zoneId, status);
	}

	public Event getEvent(int id) {
		return eventMapper.selectById(id);
	}

	/** @return 갱신된 Event, 없는 이벤트면 null */
	public Event updateStatus(int id, String status, String memo, Integer handledByUserId) {
		Event existing = eventMapper.selectById(id);
		if (existing == null) {
			return null;
		}

		String handledAt = LocalDateTime.now().format(MYSQL_DATETIME);
		int result = eventMapper.updateStatus(id, status, memo, handledByUserId, handledAt);
		if (result <= 0) {
			return null;
		}

		existing.setStatus(status);
		existing.setMemo(memo);
		existing.setHandledBy(handledByUserId);
		existing.setHandledAt(handledAt);
		return existing;
	}

	public List<EventThreshold> getThresholds(int zoneId) {
		List<EventThreshold> thresholds = eventMapper.selectThresholdsByZone(zoneId);
		if (!thresholds.isEmpty()) {
			return thresholds;
		}
		return List.of(
				new EventThreshold(zoneId, 1, DEFAULT_CAUTION),
				new EventThreshold(zoneId, 2, DEFAULT_DANGER));
	}

	public void setThresholds(int zoneId, List<EventThreshold> thresholds) {
		eventMapper.deleteThresholdsByZone(zoneId);
		for (EventThreshold t : thresholds) {
			t.setZoneId(zoneId);
			eventMapper.upsertThreshold(t);
		}
	}

}
