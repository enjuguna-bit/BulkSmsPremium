package com.afriserve.smsmanager.data.csv;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Utility class for intelligently splitting full names into components.
 * Handles various name formats including:
 * - Simple names: "John Doe"
 * - Multiple names: "John Michael Doe"
 * - Names with extra spaces: "John   Michael   Doe"
 * - Mixed case: "JOHN DOE", "john doe"
 * - Names with titles: "Mr. John Doe", "Dr. Jane Smith"
 * - Names with suffixes: "John Doe Jr.", "John Doe III"
 * - African naming conventions: "FirstName MiddleName LastName"
 */
public class NameSplitter {
    private static final String TAG = "NameSplitter";

    // Common titles to strip from names
    private static final List<String> TITLES = Arrays.asList(
        "mr", "mrs", "ms", "miss", "dr", "prof", "rev", "hon", "capt", "col", "gen",
        "lt", "sgt", "cmdr", "maj", "sir", "madam", "lady", "lord"
    );

    // Common suffixes to handle
    private static final List<String> SUFFIXES = Arrays.asList(
        "jr", "sr", "ii", "iii", "iv", "v", "esq", "phd", "md", "dds", "jd", "rn"
    );

    // Patterns that indicate a surname-first format (e.g., "Doe, John")
    private static final Pattern COMMA_PATTERN = Pattern.compile(".*,.*");

    // Single word names that are likely first names
    private static final List<String> COMMON_SINGLE_FIRST_NAMES = Arrays.asList(
        "john", "james", "mary", "patricia", "jennifer", "linda", "elizabeth", 
        "barbara", "susan", "jessica", "sarah", "karen", "nancy", "lisa", 
        "margaret", "betty", "sandra", "ashley", "emily", "donna", "michele",
        "david", "robert", "michael", "richard", "william", "joseph", "thomas",
        "charles", "christopher", "daniel", "matthew", "anthony", "mark", "donald",
        "paul", "andrew", "joshua", "kenneth", "kevin", "brian", "george",
        "edward", "ronald", "timothy", "jason", "jeffrey", "ryan", "jacob",
        "nicholas", "gary", "eric", "jonathan", "steven", "stephen", "larry",
        "justin", "scott", "brandon", "benjamin", "samuel", "raymond", "gregory",
        "frank", "alexander", "patrick", "jack", "dennis", "jerry", "tyler",
        "aaron", "jose", "adam", "nathan", "henry", "douglas", "zachary", "peter"
    );

    /**
     * Result container for split name components
     */
    public static class SplitName {
        public final String originalName;
        public final String firstName;
        public final String secondName;
        public final String thirdName;
        public final String lastName;
        public final String suffix;
        public final int totalParts;
        public final String[] allParts;

        public SplitName(String originalName, String firstName, String secondName,
                        String thirdName, String lastName, String suffix, int totalParts,
                        String[] allParts) {
            this.originalName = originalName;
            this.firstName = firstName;
            this.secondName = secondName;
            this.thirdName = thirdName;
            this.lastName = lastName;
            this.suffix = suffix;
            this.totalParts = totalParts;
            this.allParts = allParts;
        }

        /**
         * Get the appropriate placeholder value for a given part name
         */
        public String getPart(String partName) {
            if (partName == null) return "";
            
            String lowerPart = partName.toLowerCase().trim();
            switch (lowerPart) {
                case "firstname":
                case "first_name":
                case "fname":
                case "given":
                case "givenname":
                    return firstName != null ? firstName : "";
                    
                case "secondname":
                case "second_name":
                case "middlename":
                case "middle_name":
                case "mname":
                case "surname":
                    return secondName != null ? secondName : "";
                    
                case "thirdname":
                case "third_name":
                case "othername":
                case "other_name":
                case "tname":
                    return thirdName != null ? thirdName : "";
                    
                case "lastname":
                case "last_name":
                case "lname":
                case "family":
                case "familyname":
                    return lastName != null ? lastName : "";
                    
                case "fullname":
                case "full_name":
                case "name":
                    return originalName != null ? originalName : "";
                    
                default:
                    return "";
            }
        }

        @Override
        public String toString() {
            return "SplitName{" +
                    "firstName='" + firstName + '\'' +
                    ", secondName='" + secondName + '\'' +
                    ", thirdName='" + thirdName + '\'' +
                    ", lastName='" + lastName + '\'' +
                    ", suffix='" + suffix + '\'' +
                    '}';
        }
    }

    /**
     * Split a full name into components
     * @param fullName The full name to split
     * @return SplitName containing all name components
     */
    public static SplitName split(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return new SplitName("", "", "", "", "", "", 0, new String[0]);
        }

        String original = fullName.trim();
        String cleaned = cleanName(original);
        
        if (cleaned.isEmpty()) {
            return new SplitName(original, "", "", "", "", "", 0, new String[0]);
        }

        // Handle comma-separated format (Surname, Firstname)
        if (COMMA_PATTERN.matcher(cleaned).matches()) {
            return splitSurnameFirst(cleaned, original);
        }

        return splitFirstNameFirst(cleaned, original);
    }

    /**
     * Check if a header name likely contains full names that should be split
     */
    public static boolean isNameColumn(String headerName) {
        if (headerName == null) return false;
        
        String lower = headerName.toLowerCase().trim()
            .replace("_", "")
            .replace(" ", "");

        // Direct name column indicators
        if (lower.contains("fullname") || lower.contains("full_name") ||
            lower.equals("name") || lower.equals("names") ||
            lower.equals("customer") || lower.equals("client") ||
            lower.equals("borrower") || lower.equals("customer_name") ||
            lower.contains("customername") || lower.contains("clientname")) {
            return true;
        }

        // Exclude columns that are clearly not names
        if (lower.contains("phone") || lower.contains("email") || 
            lower.contains("address") || lower.contains("amount") ||
            lower.contains("balance") || lower.contains("loan") ||
            lower.contains("date") || lower.contains("id") ||
            lower.contains("number")) {
            return false;
        }

        return false;
    }

    /**
     * Generate placeholder names for name splitting
     * @return Array of placeholder names like {"{firstname}", "{secondname}", "{thirdname}"}
     */
    public static String[] getNamePlaceholders() {
        return new String[]{"{firstname}", "{secondname}", "{thirdname}"};
    }

    /**
     * Clean a name by removing extra spaces, normalizing case, and handling special characters
     */
    private static String cleanName(String name) {
        if (name == null) return "";
        
        String cleaned = name
            .replace("\uFEFF", "")  // BOM
            .replace("\u200B", "")  // Zero-width space
            .replace("\u00A0", " ") // Non-breaking space
            .replaceAll("\\s+", " ") // Multiple spaces to single
            .trim();

        // Remove common titles
        for (String title : TITLES) {
            String pattern = "\\b" + title + "\\.?" + "\\s+";
            cleaned = cleaned.replaceAll(pattern, "");
        }

        // Remove titles at start
        if (cleaned.toLowerCase().startsWith("mr ") ||
            cleaned.toLowerCase().startsWith("mrs ") ||
            cleaned.toLowerCase().startsWith("ms ") ||
            cleaned.toLowerCase().startsWith("miss ") ||
            cleaned.toLowerCase().startsWith("dr ") ||
            cleaned.toLowerCase().startsWith("prof ")) {
            int firstSpace = cleaned.indexOf(' ');
            if (firstSpace > 0) {
                cleaned = cleaned.substring(firstSpace + 1).trim();
            }
        }

        return cleaned.trim();
    }

    /**
     * Extract suffix from name (e.g., "Jr", "III", "PhD")
     */
    private static String extractSuffix(String[] parts) {
        if (parts.length < 2) return "";
        
        String last = parts[parts.length - 1].toLowerCase().replace(".", "");
        for (String suffix : SUFFIXES) {
            if (last.equals(suffix)) {
                return parts[parts.length - 1];
            }
        }
        
        // Check for numeric suffixes like II, III, IV
        if (last.matches("^(ii|iii|iv|v)$")) {
            return parts[parts.length - 1].toUpperCase();
        }
        
        return "";
    }

    /**
     * Split names in "Surname, Firstname" format
     */
    private static SplitName splitSurnameFirst(String name, String original) {
        String[] parts = name.split("\\s*,\\s*");
        
        if (parts.length == 1) {
            return splitFirstNameFirst(parts[0].trim(), original);
        }

        String surname = capitalize(parts[0].trim());
        String remaining = parts.length > 1 ? parts[1].trim() : "";
        
        // Check for suffix in remaining part
        String suffix = "";
        String[] remainingParts = remaining.split("\\s+");
        
        if (remainingParts.length > 1) {
            String potentialSuffix = remainingParts[remainingParts.length - 1].toLowerCase().replace(".", "");
            for (String s : SUFFIXES) {
                if (potentialSuffix.equals(s)) {
                    suffix = remainingParts[remainingParts.length - 1];
                    remaining = remaining.substring(0, remaining.lastIndexOf(' ')).trim();
                    remainingParts = remaining.split("\\s+");
                    break;
                }
            }
        }

        String[] remainingNameParts = remaining.split("\\s+");
        String firstName = remainingNameParts.length > 0 ? capitalize(remainingNameParts[0]) : "";
        String secondName = remainingNameParts.length > 1 ? capitalize(remainingNameParts[1]) : "";
        String thirdName = remainingNameParts.length > 2 ? capitalize(remainingNameParts[2]) : "";
        
        String[] allParts = new String[remainingNameParts.length + 1];
        System.arraycopy(remainingNameParts, 0, allParts, 0, remainingNameParts.length);
        allParts[allParts.length - 1] = surname;

        return new SplitName(original, firstName, secondName, thirdName, surname, suffix, 
                            allParts.length, allParts);
    }

    /**
     * Split names in "Firstname Lastname" format
     */
    private static SplitName splitFirstNameFirst(String name, String original) {
        // Remove suffix first if present
        String suffix = "";
        String workingName = name;
        
        String[] parts = name.split("\\s+");
        if (parts.length >= 2) {
            String potentialSuffix = parts[parts.length - 1].toLowerCase().replace(".", "");
            for (String s : SUFFIXES) {
                if (potentialSuffix.equals(s)) {
                    suffix = parts[parts.length - 1];
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < parts.length - 1; i++) {
                        if (sb.length() > 0) sb.append(" ");
                        sb.append(parts[i]);
                    }
                    workingName = sb.toString();
                    parts = workingName.split("\\s+");
                    break;
                }
            }
        }

        if (parts.length == 0 || parts[0].isEmpty()) {
            return new SplitName(original, "", "", "", "", "", 0, new String[0]);
        }

        // Single name - treat as first name
        if (parts.length == 1) {
            String firstName = capitalize(parts[0]);
            return new SplitName(original, firstName, "", "", "", suffix, 1, new String[]{firstName});
        }

        // Two names - typically first and last
        if (parts.length == 2) {
            String firstName = capitalize(parts[0]);
            String lastName = capitalize(parts[1]);
            return new SplitName(original, firstName, "", "", lastName, suffix, 2, 
                                new String[]{firstName, lastName});
        }

        // Three or more names
        String firstName = capitalize(parts[0]);
        String lastName = capitalize(parts[parts.length - 1]);
        String secondName = capitalize(parts[1]);
        String thirdName = parts.length > 3 ? capitalize(parts[2]) : "";

        // Build all parts array
        String[] allParts = new String[parts.length];
        for (int i = 0; i < parts.length; i++) {
            allParts[i] = capitalize(parts[i]);
        }

        return new SplitName(original, firstName, secondName, thirdName, lastName, suffix,
                            allParts.length, allParts);
    }

    /**
     * Capitalize first letter of each word
     */
    private static String capitalize(String text) {
        if (text == null || text.isEmpty()) return "";
        
        String[] words = text.split("\\s+");
        StringBuilder result = new StringBuilder();
        
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (result.length() > 0) result.append(" ");
            result.append(word.substring(0, 1).toUpperCase())
                  .append(word.substring(1).toLowerCase());
        }
        
        return result.toString();
    }

    /**
     * Check if a name part looks like a surname (used for intelligent splitting)
     */
    private static boolean isLikelySurname(String namePart) {
        if (namePart == null || namePart.length() < 2) return false;
        
        // Common surname indicators
        String lower = namePart.toLowerCase();
        
        // Names ending with common surname suffixes
        if (lower.endsWith("son") || lower.endsWith("sen") ||
            lower.endsWith("berg") || lower.endsWith("stein") ||
            lower.endsWith("man") || lower.endsWith("ton") ||
            lower.endsWith("ward") || lower.endsWith("ling")) {
            return true;
        }
        
        // If it's a single common first name, it's not a surname
        if (COMMON_SINGLE_FIRST_NAMES.contains(lower)) {
            return false;
        }
        
        // Longer names are more likely surnames
        return namePart.length() > 4;
    }
}
