package com.dronecontrol.stats;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/** 구역 분단위 통계를 엑셀(xlsx)로 내보낸다 (export-001). */
@Service
public class StatsExportService {

	private static final String[] HEADERS = {
			"시각", "평균인원", "최대인원", "최소인원", "밀집도(명/㎡)",
			"유입N", "유입E", "유입S", "유입W", "유출N", "유출E", "유출S", "유출W",
			"평균체류(초)", "평균속도(px/s)",
	};

	public byte[] buildWorkbook(String zoneName, List<ZoneMinuteStat> stats) {
		try (XSSFWorkbook wb = new XSSFWorkbook()) {
			XSSFSheet sheet = wb.createSheet(zoneName == null ? "구역 통계" : zoneName);

			Font headerFont = wb.createFont();
			headerFont.setBold(true);
			headerFont.setColor(org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());
			CellStyle headerStyle = wb.createCellStyle();
			headerStyle.setFont(headerFont);
			headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.DARK_BLUE.getIndex());
			headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

			Row headerRow = sheet.createRow(0);
			for (int c = 0; c < HEADERS.length; c++) {
				Cell cell = headerRow.createCell(c);
				cell.setCellValue(HEADERS[c]);
				cell.setCellStyle(headerStyle);
			}

			int r = 1;
			for (ZoneMinuteStat s : stats) {
				Row row = sheet.createRow(r++);
				int c = 0;
				row.createCell(c++).setCellValue(s.getAt());
				row.createCell(c++).setCellValue(s.getCountAvg() == null ? 0 : s.getCountAvg().doubleValue());
				row.createCell(c++).setCellValue(s.getCountMax());
				row.createCell(c++).setCellValue(s.getCountMin());
				row.createCell(c++).setCellValue(s.getDensity() == null ? 0 : s.getDensity().doubleValue());
				row.createCell(c++).setCellValue(s.getInflowN());
				row.createCell(c++).setCellValue(s.getInflowE());
				row.createCell(c++).setCellValue(s.getInflowS());
				row.createCell(c++).setCellValue(s.getInflowW());
				row.createCell(c++).setCellValue(s.getOutflowN());
				row.createCell(c++).setCellValue(s.getOutflowE());
				row.createCell(c++).setCellValue(s.getOutflowS());
				row.createCell(c++).setCellValue(s.getOutflowW());
				row.createCell(c++).setCellValue(s.getDwellAvgSec());
				row.createCell(c++).setCellValue(s.getAvgSpeedPxS() == null ? 0 : s.getAvgSpeedPxS().doubleValue());
			}

			for (int c = 0; c < HEADERS.length; c++) {
				sheet.autoSizeColumn(c);
			}
			sheet.createFreezePane(0, 1);

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			wb.write(out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException("엑셀 생성 실패", e);
		}
	}

}
