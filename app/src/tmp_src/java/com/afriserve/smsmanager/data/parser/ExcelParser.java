package com.afriserve.smsmanager.data.parser;

import android.util.Log;
import com.afriserve.smsmanager.models.Recipient;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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
        
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        
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
        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = createWorkbook(fis, filePath)) {
            
            // Get the first worksheet
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IOException("Excel file has no worksheets");
            }
            
            // Get headers from first row
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IOException("Excel file has no header row");
            }
            
            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(getCellValueAsString(cell).trim());
            }
            
            // Parse data rows
            List<Map<String, String>> data = new ArrayList<>();
            
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isEmptyRow(row)) {
                    continue; // Skip empty rows
                }
                
                Map<String, String> rowObject = new HashMap<>();
                
                for (int j = 0; j < headers.size(); j++) {
                    String header = headers.get(j);
                    Cell cell = row.getCell(j);
                    String value = getCellValueAsString(cell).trim();
                    rowObject.put(header, value);
                }
                
                data.add(rowObject);
            }
            
            Log.d(TAG, "Parsed " + data.size() + " rows from Excel file");
            return data;
            
        } catch (Exception e) {
            Log.e(TAG, "Excel parsing error", e);
            throw new IOException("Failed to parse Excel file: " + e.getMessage(), e);
        }
    }
    
    /**
     * Create appropriate workbook based on file extension
     */
    private static Workbook createWorkbook(InputStream inputStream, String filePath) throws IOException {
        String extension = filePath.substring(filePath.lastIndexOf('.') + 1).toLowerCase();
        
        switch (extension) {
            case "xlsx":
                return new XSSFWorkbook(inputStream);
            case "xls":
                return new HSSFWorkbook(inputStream);
            default:
                throw new IOException("Unsupported Excel format: " + extension);
        }
    }
    
    /**
     * Get cell value as string
     */
    private static String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    // Handle large numbers without scientific notation
                    double numericValue = cell.getNumericCellValue();
                    if (numericValue > 1000000000L) {
                        // This might be a phone number, format as string
                        return String.format("%.0f", numericValue);
                    }
                    return String.valueOf(numericValue);
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    return cell.getStringCellValue();
                }
            case BLANK:
                return "";
            default:
                return "";
        }
    }
    
    /**
     * Check if row is empty
     */
    private static boolean isEmptyRow(Row row) {
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String value = getCellValueAsString(cell).trim();
                if (!value.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
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
            
            recipients.add(new Recipient(name, phone, amount, false, null));
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
     * Clean cell values (remove invisible characters, trim)
     */
    private static String cleanCellValue(Object value) {
        if (value == null) return "";
        return value.toString()
            .replaceAll("[\\u200B\\u00A0]", "")
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
