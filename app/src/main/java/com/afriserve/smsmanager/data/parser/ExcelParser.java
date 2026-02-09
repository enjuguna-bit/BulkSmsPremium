package com.afriserve.smsmanager.data.parser;

import android.util.Log;
import com.afriserve.smsmanager.models.Recipient;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Excel file parser supporting .xlsx, .xls, and CSV formats
 * Uses Apache POI for Excel files and existing CSV parser
 */
public class ExcelParser {
    private static final String TAG = "ExcelParser";
    
    // Header aliases for smart column mapping
    private static final Map<String, List<String>> HEADER_ALIASES = new HashMap<>();
    
    static {
        // Name column aliases
        HEADER_ALIASES.put("name", List.of(
            "FullNames", "Full Name", "CustomerName", "Name", "Client", "Customer", "Contact Name"
        ));
        
        // Phone column aliases
        HEADER_ALIASES.put("phone", List.of(
            "PhoneNumber", "Phone", "MobilePhone", "Contact", "Phone No", "Mobile", "Telephone", "Tel"
        ));
        
        // Amount column aliases
        HEADER_ALIASES.put("amount", List.of(
            "Arrears Amount", "Amount", "Balance", "Loan", "Cost", "Arrears", "Payment", "Due"
        ));
    }
    
    /**
     * Supported file types for import
     */
    public enum ImportFileType {
        CSV("csv"),
        XLSX("xlsx"),
        XLS("xls"),
        XLSM("xlsm"),
        XLT("xlt"),
        XLTX("xltx"),
        XLTM("xltm"),
        ODS("ods"),
        ODT("odt"),
        TXT("txt"),
        TSV("tsv"),
        UNKNOWN("unknown");
        
        private final String extension;
        
        ImportFileType(String extension) {
            this.extension = extension;
        }
        
        public String getExtension() {
            return extension;
        }
    }
    
    /**
     * Detect file type from file name and extension
     */
    public static ImportFileType detectFileType(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return ImportFileType.UNKNOWN;
        }

        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return ImportFileType.UNKNOWN;
        }

        String extension = fileName.substring(dotIndex + 1).toLowerCase(Locale.US);
        
        switch (extension) {
            case "csv":
                return ImportFileType.CSV;
            case "xlsx":
                return ImportFileType.XLSX;
            case "xls":
                return ImportFileType.XLS;
            case "xlsm":
                return ImportFileType.XLSM;
            case "xlt":
                return ImportFileType.XLT;
            case "xltx":
                return ImportFileType.XLTX;
            case "xltm":
                return ImportFileType.XLTM;
            case "ods":
                return ImportFileType.ODS;
            case "odt":
                return ImportFileType.ODT;
            case "txt":
                return ImportFileType.TXT;
            case "tsv":
                return ImportFileType.TSV;
            default:
                return ImportFileType.UNKNOWN;
        }
    }
    
    /**
     * Parse Excel file (.xlsx or .xls) and convert to CSV-like format
     */
    public static List<Map<String, String>> parseExcelFile(String filePath) throws IOException {
        ParsedSheet parsed = parseExcelFileWithHeaders(filePath);
        return parsed.rows;
    }

    /**
     * Parse Excel file with headers preserved
     */
    public static ParsedSheet parseExcelFileWithHeaders(String filePath) throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            return parseExcelStreamWithHeaders(fis);
        }
    }

    /**
     * Parse Excel InputStream with headers preserved
     */
    public static ParsedSheet parseExcelStreamWithHeaders(InputStream inputStream) throws IOException {
        try (Workbook workbook = createWorkbook(inputStream)) {
            Sheet sheet = findFirstNonEmptySheet(workbook);
            if (sheet == null) {
                throw new IOException("Excel file has no worksheets");
            }

            DataFormatter formatter = new DataFormatter(Locale.US, true);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            Row headerRow = findHeaderRow(sheet, formatter, evaluator);
            if (headerRow == null) {
                throw new IOException("Excel file has no header row");
            }

            int headerRowIndex = headerRow.getRowNum();
            int lastCell = Math.max(headerRow.getLastCellNum(), headerRow.getPhysicalNumberOfCells());
            if (lastCell <= 0) {
                throw new IOException("Excel file has empty header row");
            }

            List<String> headers = new ArrayList<>();
            Set<String> usedHeaders = new HashSet<>();
            for (int i = 0; i < lastCell; i++) {
                Cell cell = headerRow.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                String header = cleanHeaderValue(getCellValueAsString(cell, formatter, evaluator));
                if (header.isEmpty()) {
                    header = "Column" + (i + 1);
                }
                header = makeUniqueHeader(header, usedHeaders);
                headers.add(header);
            }

            List<Map<String, String>> data = new ArrayList<>();
            for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isEmptyRow(row, headers.size(), formatter, evaluator)) {
                    continue;
                }

                Map<String, String> rowObject = new LinkedHashMap<>();
                for (int j = 0; j < headers.size(); j++) {
                    String header = headers.get(j);
                    Cell cell = row.getCell(j, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String value = cleanCellValue(getCellValueAsString(cell, formatter, evaluator));
                    rowObject.put(header, value);
                }
                data.add(rowObject);
            }

            Log.d(TAG, "Parsed " + data.size() + " rows from Excel file");
            return new ParsedSheet(headers, data);

        } catch (Exception e) {
            Log.e(TAG, "Excel parsing error", e);
            throw new IOException("Failed to parse Excel file: " + e.getMessage(), e);
        }
    }
    
    /**
     * Create appropriate workbook using Apache POI auto-detection
     */
    private static Workbook createWorkbook(InputStream inputStream) throws IOException {
        return WorkbookFactory.create(inputStream);
    }
    
    /**
     * Get cell value as string using formatter and evaluator
     */
    private static String getCellValueAsString(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }

        String formatted = formatter != null
            ? formatter.formatCellValue(cell, evaluator)
            : cell.toString();

        if (formatted == null) {
            formatted = "";
        }

        // Avoid scientific notation for large numeric values
        if (formatted.contains("E") || formatted.contains("e")) {
            try {
                if (cell.getCellType() == CellType.NUMERIC || cell.getCellType() == CellType.FORMULA) {
                    double numericValue = cell.getNumericCellValue();
                    formatted = new BigDecimal(Double.toString(numericValue)).toPlainString();
                }
            } catch (Exception ignored) {
            }
        }

        return formatted;
    }
    
    /**
     * Check if row is empty with a known column count
     */
    private static boolean isEmptyRow(Row row, int maxCells, DataFormatter formatter, FormulaEvaluator evaluator) {
        int cellCount = Math.max(maxCells, row != null ? row.getLastCellNum() : 0);
        for (int i = 0; i < cellCount; i++) {
            Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            String value = cleanCellValue(getCellValueAsString(cell, formatter, evaluator));
            if (!value.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Find the first sheet with data
     */
    private static Sheet findFirstNonEmptySheet(Workbook workbook) {
        if (workbook == null || workbook.getNumberOfSheets() == 0) {
            return null;
        }
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            if (sheet != null && sheet.getPhysicalNumberOfRows() > 0) {
                return sheet;
            }
        }
        return workbook.getSheetAt(0);
    }

    /**
     * Find the first non-empty row to use as headers
     */
    private static Row findHeaderRow(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        int firstRow = sheet.getFirstRowNum();
        int lastRow = sheet.getLastRowNum();
        for (int i = firstRow; i <= lastRow; i++) {
            Row row = sheet.getRow(i);
            if (row != null && !isEmptyRow(row, row.getLastCellNum(), formatter, evaluator)) {
                return row;
            }
        }
        return null;
    }

    /**
     * Ensure header name uniqueness (case-insensitive)
     */
    private static String makeUniqueHeader(String header, Set<String> usedHeaders) {
        String base = header;
        String key = base.toLowerCase(Locale.US);
        if (!usedHeaders.contains(key)) {
            usedHeaders.add(key);
            return base;
        }

        int index = 2;
        while (true) {
            String candidate = base + "_" + index;
            String candidateKey = candidate.toLowerCase(Locale.US);
            if (!usedHeaders.contains(candidateKey)) {
                usedHeaders.add(candidateKey);
                return candidate;
            }
            index++;
        }
    }

    /**
     * Clean header values (strip BOM, trim)
     */
    private static String cleanHeaderValue(String value) {
        if (value == null) return "";
        String cleaned = value.replace("\uFEFF", "").trim();
        return cleaned;
    }
    
    /**
     * Enhanced parser with smart column mapping
     * Auto-detects file type and parses accordingly
     */
    public static ParseResult parseImportFile(String filePath, String fileName) throws IOException {
        ImportFileType fileType = detectFileType(fileName);
        
        List<Map<String, String>> data;
        
        switch (fileType) {
            case CSV:
            case TXT:
            case TSV:
                data = CsvParser.parseCsvFile(filePath);
                break;
            case XLSX:
            case XLS:
            case XLSM:
            case XLT:
            case XLTX:
            case XLTM:
                data = parseExcelFile(filePath);
                break;
            case ODS:
            case ODT:
                // Try to parse as Excel (Apache POI has limited ODS support)
                try {
                    data = parseExcelFile(filePath);
                } catch (Exception e) {
                    throw new IOException("ODS/ODT files are not fully supported. Please save as Excel (.xlsx) or CSV format.");
                }
                break;
            default:
                throw new IOException("Unsupported file type: " + fileName + 
                    ". Please use CSV, Excel (.xlsx, .xls, .xlsm), or text files (.txt, .tsv).");
        }
        
        return parseWithSmartMapping(data);
    }
    
    /**
     * Parse data with smart column mapping
     */
    public static ParseResult parseWithSmartMapping(List<Map<String, String>> data) {
        if (data.isEmpty()) {
            return new ParseResult(new ArrayList<>(), new ColumnMapping(), new ArrayList<>());
        }
        
        // Get headers from first row
        List<String> headers = new ArrayList<>(data.get(0).keySet());
        List<String> normalizedHeaders = new ArrayList<>();
        
        for (String header : headers) {
            normalizedHeaders.add(normalizeHeader(header));
        }
        
        // Find column matches
        ColumnMapping mapping = new ColumnMapping();
        mapping.name = findColumnMatch(headers, normalizedHeaders, HEADER_ALIASES.get("name"));
        mapping.phone = findColumnMatch(headers, normalizedHeaders, HEADER_ALIASES.get("phone"));
        mapping.amount = findColumnMatch(headers, normalizedHeaders, HEADER_ALIASES.get("amount"));
        
        // Parse recipients
        List<Recipient> recipients = new ArrayList<>();
        
        for (Map<String, String> row : data) {
            Map<String, String> fields = new HashMap<>();
            for (Map.Entry<String, String> entry : row.entrySet()) {
                String normalizedKey = normalizePlaceholderHeader(entry.getKey());
                if (normalizedKey.isEmpty()) {
                    continue;
                }

                String value = cleanCellValue(entry.getValue());
                if (!fields.containsKey(normalizedKey) || fields.get(normalizedKey).isEmpty()) {
                    fields.put(normalizedKey, value);
                }
            }

            String name = cleanCellValue(
                mapping.name != null ? row.get(mapping.name) : 
                row.getOrDefault("FullNames", row.getOrDefault("Name", ""))
            );
            
            String phoneRaw = mapping.phone != null ? row.get(mapping.phone) : 
                row.getOrDefault("PhoneNumber", row.getOrDefault("Phone", ""));
            String phone = PhoneNormalizer.normalizePhone(cleanCellValue(phoneRaw));
            
            Double amount = mapping.amount != null ? 
                parseAmount(row.get(mapping.amount)) : 
                parseAmount(row.getOrDefault("Amount", null));
            
            // Skip rows without valid phone numbers after normalization
            if (phone == null || phone.isEmpty()) {
                continue;
            }
            
            recipients.add(new Recipient(name, phone, amount, false, fields));
        }
        
        Log.d(TAG, "Parsed " + recipients.size() + " valid recipients");
        return new ParseResult(recipients, mapping, data);
    }
    
    /**
     * Find column match using aliases
     */
    private static String findColumnMatch(List<String> headers, List<String> normalizedHeaders, List<String> aliases) {
        for (String alias : aliases) {
            String normalizedAlias = normalizeHeader(alias);
            int index = normalizedHeaders.indexOf(normalizedAlias);
            if (index != -1) {
                return headers.get(index);
            }
        }
        return null;
    }
    
    /**
     * Normalize headers for fuzzy comparison
     */
    private static String normalizeHeader(String header) {
        if (header == null) return "";
        return header.toLowerCase()
            .replaceAll("\\s+", "")
            .replaceAll("[^\\w]", "");
    }

    /**
     * Normalize headers for placeholder keys (matches TemplateVariableExtractor normalization)
     */
    private static String normalizePlaceholderHeader(String header) {
        if (header == null) return "";
        return header.toLowerCase()
            .replaceAll("\\s+", "_")
            .replaceAll("[^\\w_]", "")
            .replaceAll("_+", "_")
            .replaceAll("^_|_$", "");
    }
    
    /**
     * Clean cell values (remove invisible characters, trim)
     */
    private static String cleanCellValue(Object value) {
        if (value == null) return "";
        return value.toString()
            .replaceAll("[\\u200B\\u00A0\\uFEFF]", "")
            .trim();
    }
    
    /**
     * Parse amount such as "Ksh 1,200.00" → 1200.0
     */
    private static Double parseAmount(Object value) {
        String clean = cleanCellValue(value)
            .replaceAll("(?i)(Ksh|KES|ksh|kes)", "")
            .replaceAll(",", "")
            .trim();
        
        try {
            double num = Double.parseDouble(clean);
            return num;
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * Get file type display name for user feedback
     */
    public static String getFileTypeDisplayName(String fileName) {
        ImportFileType fileType = detectFileType(fileName);
        
        switch (fileType) {
            case CSV:
                return "CSV";
            case XLSX:
                return "Excel (XLSX)";
            case XLS:
                return "Excel (XLS)";
            case XLSM:
                return "Excel Macro-Enabled (XLSM)";
            case XLT:
                return "Excel Template (XLT)";
            case XLTX:
                return "Excel Template (XLTX)";
            case XLTM:
                return "Excel Macro Template (XLTM)";
            case ODS:
                return "OpenDocument Spreadsheet (ODS)";
            case ODT:
                return "OpenDocument Text (ODT)";
            case TXT:
                return "Text File (TXT)";
            case TSV:
                return "Tab-Separated Values (TSV)";
            default:
                return "Unknown";
        }
    }
    
    /**
     * Validate if file type is supported
     */
    public static boolean isFileTypeSupported(String fileName) {
        return detectFileType(fileName) != ImportFileType.UNKNOWN;
    }
    
    /**
     * Parse result data class
     */
    public static class ParseResult {
        public final List<Recipient> recipients;
        public final ColumnMapping mapping;
        public final List<Map<String, String>> rawData;
        
        public ParseResult(List<Recipient> recipients, ColumnMapping mapping, List<Map<String, String>> rawData) {
            this.recipients = recipients;
            this.mapping = mapping;
            this.rawData = rawData;
        }
    }

    /**
     * Parsed Excel sheet with headers
     */
    public static class ParsedSheet {
        public final List<String> headers;
        public final List<Map<String, String>> rows;

        public ParsedSheet(List<String> headers, List<Map<String, String>> rows) {
            this.headers = headers;
            this.rows = rows;
        }
    }
    
    /**
     * Column mapping data class
     */
    public static class ColumnMapping {
        public String name;
        public String phone;
        public String amount;
        
        public ColumnMapping() {}
        
        public ColumnMapping(String name, String phone, String amount) {
            this.name = name;
            this.phone = phone;
            this.amount = amount;
        }
        
        @Override
        public String toString() {
            return "ColumnMapping{name='" + name + "', phone='" + phone + "', amount='" + amount + "'}";
        }
    }
}
