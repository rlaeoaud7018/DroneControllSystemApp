package com.dronecontrol.stats;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.dronecontrol.event.EventService;

@Service
public class StatsService {

	private static final DateTimeFormatter MYSQL_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	@Autowired
	private StatsMapper statsMapper;

	@Autowired
	private EventService eventService;

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
