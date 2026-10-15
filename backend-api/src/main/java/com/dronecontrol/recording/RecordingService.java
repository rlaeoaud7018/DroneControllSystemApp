package com.dronecontrol.recording;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RecordingService {

	private static final DateTimeFormatter MYSQL_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	@Autowired
	private RecordingMapper recordingMapper;

	/** DUMMY 캠이 RUNNING으로 전환될 때마다 "이 영상이 재생되었다"는 녹화 이력을 한 건 남긴다. */
	public void recordPlaybackStart(int cameraId, String sourceUri) {
		recordingMapper.insertRecording(cameraId, LocalDateTime.now().format(MYSQL_DATETIME), sourceUri);
	}

	public List<Recording> getRecordings(int zoneId, String from, String to) {
		return recordingMapper.selectByZoneAndRange(zoneId, from, to);
	}

}
