package com.afriserve.smsmanager.data.templates;

import android.util.Log;
import android.util.Patterns;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Advanced Template Engine for SMS message processing
 * Supports variables, functions, and conditional logic
 */
@Singleton
public class TemplateEngine {
    private static final String TAG = "TemplateEngine";
    
    // Pattern for template variables {variableName}
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{([^}]+)\\}");
    
    // Pattern for functions {{functionName(args)}}
    private static final Pattern FUNCTION_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");
    
    // Pattern for conditional blocks {?if condition}...{?else}...{?endif}
    private static final Pattern CONDITIONAL_PATTERN = Pattern.compile("\\{\\?if\\s+([^}]+)\\}([^\\?]*)\\{\\?else\\}([^\\?]*)\\{\\?endif\\}");
    
    // Built-in functions
    private final Map<String, TemplateFunction> functions;
    
    @Inject
    public TemplateEngine() {
        this.functions = new HashMap<>();
        initializeBuiltInFunctions();
    }
    
    /**
     * Process template with given data
     */
    public String processTemplate(String template, Map<String, String> data) {
        if (template == null || template.trim().isEmpty()) {
            return "";
        }
        
        try {
            String result = template;
            
            // Process conditionals first
            result = processConditionals(result, data);
            
            // Process functions
            result = processFunctions(result, data);
            
            // Process variables
            result = processVariables(result, data);
            
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing template", e);
            return template; // Return original template on error
        }
    }
    
    /**
     * Extract variables from template
     */
    public List<String> extractVariables(String template) {
        List<String> variables = new ArrayList<>();
        if (template == null) return variables;
        
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        while (matcher.find()) {
            String variable = matcher.group(1).trim();
            if (!variables.contains(variable)) {
                variables.add(variable);
            }
        }
        
        return variables;
    }
    
    /**
     * Validate template syntax
     */
    public TemplateValidationResult validateTemplate(String template) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        if (template == null || template.trim().isEmpty()) {
            errors.add("Template cannot be empty");
            return new TemplateValidationResult(false, errors, warnings);
        }
        
        // Check for unmatched conditionals
        int ifCount = countOccurrences(template, "{?if");
        int endifCount = countOccurrences(template, "{?endif");
        if (ifCount != endifCount) {
            errors.add("Unmatched conditional blocks: " + ifCount + " {?if but " + endifCount + " {?endif");
        }
        
        // Check for invalid function syntax
        Matcher functionMatcher = FUNCTION_PATTERN.matcher(template);
        while (functionMatcher.find()) {
            String functionCall = functionMatcher.group(1);
            if (!isValidFunctionCall(functionCall)) {
                errors.add("Invalid function syntax: " + functionCall);
            }
        }
        
        // Check for very long templates
        if (template.length() > 1000) {
            warnings.add("Template is very long (" + template.length() + " characters). Consider splitting into multiple messages.");
        }
        
        // Check for SMS length limits
        String processed = processTemplate(template, new HashMap<>());
        if (processed.length() > 160) {
            warnings.add("Processed message exceeds 160 characters (" + processed.length() + "). May be split into multiple SMS.");
        }
        
        return new TemplateValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    /**
     * Get template statistics
     */
    public TemplateStats getTemplateStats(String template) {
        List<String> variables = extractVariables(template);
        List<String> functions = extractFunctions(template);
        int conditionalBlocks = countOccurrences(template, "{?if");
        
        return new TemplateStats(
            template.length(),
            variables.size(),
            functions.size(),
            conditionalBlocks,
            variables,
            functions
        );
    }
    
    /**
     * Process template variables
     */
    private String processVariables(String template, Map<String, String> data) {
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String variableName = matcher.group(1).trim();
            String value = getVariableValue(variableName, data);
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    /**
     * Process template functions
     */
    private String processFunctions(String template, Map<String, String> data) {
        Matcher matcher = FUNCTION_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String functionCall = matcher.group(1).trim();
            String value = processFunction(functionCall, data);
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    /**
     * Process conditional blocks
     */
    private String processConditionals(String template, Map<String, String> data) {
        Matcher matcher = CONDITIONAL_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String condition = matcher.group(1).trim();
            String ifContent = matcher.group(2);
            String elseContent = matcher.group(3);
            
            String content = evaluateCondition(condition, data) ? ifContent : elseContent;
            matcher.appendReplacement(result, Matcher.quoteReplacement(content));
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    /**
     * Get variable value with fallback
     */
    private String getVariableValue(String variableName, Map<String, String> data) {
        // Direct lookup
        String value = data.get(variableName);
        if (value != null) return value;
        
        // Case-insensitive lookup
        for (Map.Entry<String, String> entry : data.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(variableName)) {
                return entry.getValue();
            }
        }
        
        // Default values
        switch (variableName.toLowerCase()) {
            case "date":
            case "today":
                return new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(new Date());
            case "time":
                return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
            case "datetime":
                return new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(new Date());
            default:
                return "{" + variableName + "}"; // Return original if not found
        }
    }
    
    /**
     * Process function call
     */
    private String processFunction(String functionCall, Map<String, String> data) {
        int parenIndex = functionCall.indexOf('(');
        if (parenIndex == -1 || !functionCall.endsWith(")")) {
            return "{{" + functionCall + "}}"; // Return original if invalid
        }
        
        String functionName = functionCall.substring(0, parenIndex).trim();
        String argsString = functionCall.substring(parenIndex + 1, functionCall.length() - 1).trim();
        
        TemplateFunction function = functions.get(functionName.toLowerCase());
        if (function == null) {
            return "{{" + functionCall + "}}"; // Return original if function not found
        }
        
        String[] args = argsString.isEmpty() ? new String[0] : argsString.split(",");
        for (int i = 0; i < args.length; i++) {
            args[i] = args[i].trim().replaceAll("^\"|\"$", ""); // Remove quotes
        }
        
        try {
            return function.execute(args, data);
        } catch (Exception e) {
            Log.e(TAG, "Error executing function: " + functionName, e);
            return "{{" + functionCall + "}}";
        }
    }
    
    /**
     * Evaluate condition
     */
    private boolean evaluateCondition(String condition, Map<String, String> data) {
        condition = condition.trim();
        
        // Simple equality check: variable == "value"
        if (condition.contains("==")) {
            String[] parts = condition.split("==");
            if (parts.length == 2) {
                String variable = parts[0].trim();
                String expectedValue = parts[1].trim().replaceAll("^\"|\"$", "");
                String actualValue = getVariableValue(variable, data);
                return expectedValue.equals(actualValue);
            }
        }
        
        // Check if variable exists and is not empty
        String value = getVariableValue(condition, data);
        return value != null && !value.isEmpty() && !value.equals("{" + condition + "}");
    }
    
    /**
     * Initialize built-in functions
     */
    private void initializeBuiltInFunctions() {
        // Uppercase function
        functions.put("upper", (args, data) -> {
            if (args.length > 0) {
                String value = getVariableValue(args[0], data);
                return value.toUpperCase();
            }
            return "";
        });
        
        // Lowercase function
        functions.put("lower", (args, data) -> {
            if (args.length > 0) {
                String value = getVariableValue(args[0], data);
                return value.toLowerCase();
            }
            return "";
        });
        
        // Length function
        functions.put("length", (args, data) -> {
            if (args.length > 0) {
                String value = getVariableValue(args[0], data);
                return String.valueOf(value.length());
            }
            return "0";
        });
        
        // Default value function
        functions.put("default", (args, data) -> {
            if (args.length >= 2) {
                String value = getVariableValue(args[0], data);
                if (!value.equals("{" + args[0] + "}")) {
                    return value;
                }
                return args[1];
            }
            return "";
        });
        
        // Format phone function
        functions.put("format_phone", (args, data) -> {
            if (args.length > 0) {
                String phone = getVariableValue(args[0], data);
                return formatPhoneNumber(phone);
            }
            return "";
        });
    }
    
    /**
     * Format phone number
     */
    private String formatPhoneNumber(String phone) {
        if (phone == null) return "";
        
        // Remove all non-digit characters
        String digits = phone.replaceAll("[^0-9+]", "");
        
        // Simple formatting for US numbers
        if (digits.matches("^1?\\d{10}$")) {
            if (digits.startsWith("1")) {
                digits = digits.substring(1);
            }
            return digits.substring(0, 3) + "-" + digits.substring(3, 6) + "-" + digits.substring(6);
        }
        
        return digits; // Return as-is if not a US number
    }
    
    /**
     * Count occurrences of substring
     */
    private int countOccurrences(String text, String substring) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }
    
    /**
     * Check if function call is valid
     */
    private boolean isValidFunctionCall(String functionCall) {
        int parenIndex = functionCall.indexOf('(');
        return parenIndex > 0 && functionCall.endsWith(")");
    }
    
    /**
     * Extract functions from template
     */
    private List<String> extractFunctions(String template) {
        List<String> functions = new ArrayList<>();
        if (template == null) return functions;
        
        Matcher matcher = FUNCTION_PATTERN.matcher(template);
        while (matcher.find()) {
            String functionCall = matcher.group(1).trim();
            int parenIndex = functionCall.indexOf('(');
            if (parenIndex > 0) {
                String functionName = functionCall.substring(0, parenIndex).trim();
                if (!functions.contains(functionName)) {
                    functions.add(functionName);
                }
            }
        }
        
        return functions;
    }
    
    /**
     * Template function interface
     */
    @FunctionalInterface
    public interface TemplateFunction {
        String execute(String[] args, Map<String, String> data);
    }
    
    /**
     * Template validation result
     */
    public static class TemplateValidationResult {
        public final boolean isValid;
        public final List<String> errors;
        public final List<String> warnings;
        
        public TemplateValidationResult(boolean isValid, List<String> errors, List<String> warnings) {
            this.isValid = isValid;
            this.errors = new ArrayList<>(errors);
            this.warnings = new ArrayList<>(warnings);
        }
    }
    
    /**
     * Template statistics
     */
    public static class TemplateStats {
        public final int length;
        public final int variableCount;
        public final int functionCount;
        public final int conditionalCount;
        public final List<String> variables;
        public final List<String> functions;
        
        public TemplateStats(int length, int variableCount, int functionCount, 
                           int conditionalCount, List<String> variables, List<String> functions) {
            this.length = length;
            this.variableCount = variableCount;
            this.functionCount = functionCount;
            this.conditionalCount = conditionalCount;
            this.variables = new ArrayList<>(variables);
            this.functions = new ArrayList<>(functions);
        }
    }
}
