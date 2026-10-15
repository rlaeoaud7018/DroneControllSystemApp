package com.dronecontrol.stats;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.dronecontrol.event.EventService;

@Service
public class StatsService {

	private static final DateTimeFormatter MYSQL_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L; // 10분 — 그 이상은 클라이언트가 재연결

	@Autowired
	private StatsMapper statsMapper;

	@Autowired
	private EventService eventService;

	/** zoneId -> 구독 중인 SSE 연결 목록 (sse-001). 메모리 상주라 서버 재시작 시 초기화됨. */
	private final Map<Integer, List<SseEmitter>> emittersByZoneId = new ConcurrentHashMap<>();

	public SseEmitter subscribe(int zoneId) {
		SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
		List<SseEmitter> list = emittersByZoneId.computeIfAbsent(zoneId, k -> new CopyOnWriteArrayList<>());
		list.add(emitter);

		Runnable cleanup = () -> list.remove(emitter);
		emitter.onCompletion(cleanup);
		emitter.onTimeout(cleanup);
		emitter.onError(e -> cleanup.run());

		try {
			emitter.send(SseEmitter.event().name("connected").data(Map.of("zoneId", zoneId)));
		} catch (Exception e) {
			cleanup.run();
		}
		return emitter;
	}

	private void broadcast(int zoneId, ZoneMinuteStat stat) {
		List<SseEmitter> list = emittersByZoneId.get(zoneId);
		if (list == null || list.isEmpty()) {
			return;
		}
		for (SseEmitter emitter : list) {
			try {
				emitter.send(SseEmitter.event().name("stat").data(stat));
			} catch (Exception e) {
				list.remove(emitter);
			}
		}
	}

	/** @return 반영 성공 여부 (캠에 연결된 구역이 없으면 false) */
	public boolean recordSample(StatSample sample) {
		StatsMapper.ZoneAreaInfo zoneArea = statsMapper.selectZoneAreaByCameraId(sample.getCameraId());
		if (zoneArea == null) {
			return false;
		}

		BigDecimal density = BigDecimal.ZERO;
		if (zoneArea.getAreaM2() != null && zoneArea.getAreaM2().compareTo(BigDecimal.ZERO) > 0) {
			density = BigDecimal.valueOf(sample.getPersonCount())
					.divide(zoneArea.getAreaM2(), 3, RoundingMode.HALF_UP);
		}

		String minuteAt = toMysqlDateTime(sample.getAt(), true);
		Integer dwellAvg = averageOf(sample.getDwellSec());

		statsMapper.upsertMinuteStat(
				zoneArea.getZoneId(), minuteAt, sample.getPersonCount(), density,
				valueOr0(sample.getIn(), "N"), valueOr0(sample.getIn(), "E"),
				valueOr0(sample.getIn(), "S"), valueOr0(sample.getIn(), "W"),
				valueOr0(sample.getOut(), "N"), valueOr0(sample.getOut(), "E"),
				valueOr0(sample.getOut(), "S"), valueOr0(sample.getOut(), "W"),
				(dwellAvg == null) ? 0 : dwellAvg, sample.getAvgSpeedPxS(),
				valueOr0(sample.getMoves(), "E"), valueOr0(sample.getMoves(), "NE"),
				valueOr0(sample.getMoves(), "N"), valueOr0(sample.getMoves(), "NW"),
				valueOr0(sample.getMoves(), "W"), valueOr0(sample.getMoves(), "SW"),
				valueOr0(sample.getMoves(), "S"), valueOr0(sample.getMoves(), "SE"));

		eventService.checkAndRecord(zoneArea.getZoneId(), sample.getCameraId(), density);

		if (emittersByZoneId.containsKey(zoneArea.getZoneId())) {
			ZoneMinuteStat latest = statsMapper.selectMinuteStatAt(zoneArea.getZoneId(), minuteAt);
			if (latest != null) {
				broadcast(zoneArea.getZoneId(), latest);
			}
		}

		return true;
	}

	public List<ZoneMinuteStat> getMinuteStats(int zoneId, String from, String to) {
		return statsMapper.selectMinuteStats(zoneId, toMysqlDateTime(from, false), toMysqlDateTime(to, false));
	}

	public static String toMysqlDateTime(String isoAt, boolean truncateToMinute) {
		LocalDateTime dateTime = LocalDateTime.parse(isoAt);
		if (truncateToMinute) {
			dateTime = dateTime.withSecond(0).withNano(0);
		}
		return dateTime.format(MYSQL_DATETIME);
	}

	private int valueOr0(Map<String, Integer> map, String key) {
		if (map == null || map.get(key) == null) {
			return 0;
		}
		return map.get(key);
	}

	private Integer averageOf(List<Integer> values) {
		if (values == null || values.isEmpty()) {
			return null;
		}
		int sum = 0;
		for (int v : values) {
			sum += v;
		}
		return sum / values.size();
	}

}
