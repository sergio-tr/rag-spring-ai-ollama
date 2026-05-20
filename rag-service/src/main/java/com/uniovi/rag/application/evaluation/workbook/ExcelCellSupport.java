package com.uniovi.rag.application.evaluation.workbook;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class ExcelCellSupport {

    private ExcelCellSupport() {}

    static String cellString(Row row, Map<String, Integer> colIndex, String header, int excelRowNumber, boolean required) {
        Integer idx = colIndex.get(normalizeHeader(header));
        if (idx == null) {
            return required ? null : "";
        }
        Cell cell = row.getCell(idx);
        return cellValueToString(cell);
    }

    static String cellValueToString(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> formatNumeric(cell.getNumericCellValue());
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue().trim();
                } catch (IllegalStateException e) {
                    yield formatNumeric(cell.getNumericCellValue());
                }
            }
            case BLANK -> "";
            default -> "";
        };
    }

    private static String formatNumeric(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        DecimalFormat df = new DecimalFormat("#.####################", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return df.format(v);
    }

    static boolean rowIsCompletelyEmpty(Row row, int lastCellNum) {
        if (row == null) {
            return true;
        }
        for (int c = 0; c < lastCellNum; c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String s = cellValueToString(cell);
                if (!s.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    static String normalizeHeader(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    static Map<String, Integer> headerIndexMap(Row headerRow) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (headerRow == null) {
            return map;
        }
        short last = headerRow.getLastCellNum();
        for (int i = 0; i < last; i++) {
            Cell c = headerRow.getCell(i);
            String key = normalizeHeader(cellValueToString(c));
            if (!key.isEmpty()) {
                map.putIfAbsent(key, i);
            }
        }
        return map;
    }

    static boolean parseBooleanCell(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return "true".equals(s) || "1".equals(s) || "yes".equals(s);
    }
}
