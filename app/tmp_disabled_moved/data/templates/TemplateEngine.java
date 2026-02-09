package com.bulksms.smsmanager.data.templates;

import android.util.Log;

import com.bulksms.smsmanager.models.Recipient;

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

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Advanced template engine for SMS messages
 * Supports conditional logic, loops, functions, and advanced formatting
 */
@Singleton
public class TemplateEngine {
    
    private static final String TAG = "TemplateEngine";
    
    // Template patterns
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");
    private static final Pattern CONDITIONAL_PATTERN = Pattern.compile("\\{\\%if\\s+([^%]+)%\\}([^\\{]*?)\\{\\%endif%\\}");
    private static final Pattern LOOP_PATTERN = Pattern.compile("\\{\\%for\\s+(\\w+)\\s+in\\s+([^%]+)%\\}([^\\{]*?)\\{\\%endfor%\\}");
    private static final Pattern FUNCTION_PATTERN = Pattern.compile("\\{\\{([^}]+)\\(([^)]*)\\)\\}\\}");
    
    // Built-in functions
    private final Map<String, TemplateFunction> functions;
    
    @Inject
    public TemplateEngine() {
        this.functions = initializeFunctions();
        Log.d(TAG, "Template engine initialized with " + functions.size() + " functions");
    }
    
    /**
     * Render template with recipient data
     */
    public Single<TemplateResult> renderTemplate(String template, Recipient recipient) {
        return Single.fromCallable(() -> {
            try {
                long startTime = System.currentTimeMillis();
                
                // Create template context
                TemplateContext context = createTemplateContext(recipient);
                
                // Process template
                String rendered = processTemplate(template, context);
                
                // Validate result
                TemplateValidation validation = validateTemplate(rendered);
                
                long processingTime = System.currentTimeMillis() - startTime;
                
                return new TemplateResult(
                    rendered,
                    validation,
                    processingTime,
                    context.getVariablesUsed()
                );
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to render template", e);
                throw new RuntimeException("Template rendering failed: " + e.getMessage(), e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Validate template syntax
     */
    public TemplateValidation validateTemplateSyntax(String template) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        try {
            // Check for balanced tags
            if (!areTagsBalanced(template)) {
                errors.add("Unbalanced template tags detected");
            }
            
            // Check for invalid variables
            List<String> variables = extractVariables(template);
            for (String variable : variables) {
                if (!isValidVariableName(variable)) {
                    warnings.add("Potentially invalid variable: " + variable);
                }
            }
            
            // Check for invalid functions
            List<String> functionCalls = extractFunctionCalls(template);
            for (String functionCall : functionCalls) {
                String functionName = extractFunctionName(functionCall);
                if (!functions.containsKey(functionName)) {
                    errors.add("Unknown function: " + functionName);
                }
            }
            
            // Check for nested conditionals
            if (hasNestedConditionals(template)) {
                warnings.add("Nested conditionals detected - consider simplifying");
            }
            
        } catch (Exception e) {
            errors.add("Template validation error: " + e.getMessage());
        }
        
        return new TemplateValidation(errors.isEmpty(), errors, warnings);
    }
    
    /**
     * Get template variables
     */
    public List<String> getTemplateVariables(String template) {
        return extractVariables(template);
    }
    
    /**
     * Get template functions
     */
    public List<String> getTemplateFunctions(String template) {
        return extractFunctionCalls(template);
    }
    
    /**
     * Process template with context
     */
    private String processTemplate(String template, TemplateContext context) {
        String result = template;
        
        // Process functions first
        result = processFunctions(result, context);
        
        // Process loops
        result = processLoops(result, context);
        
        // Process conditionals
        result = processConditionals(result, context);
        
        // Process simple variables
        result = processVariables(result, context);
        
        return result;
    }
    
    /**
     * Process template functions
     */
    private String processFunctions(String template, TemplateContext context) {
        Matcher matcher = FUNCTION_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String functionName = matcher.group(1).trim();
            String args = matcher.group(2).trim();
            
            TemplateFunction function = functions.get(functionName);
            if (function != null) {
                String[] argArray = args.isEmpty() ? new String[0] : args.split("\\s*,\\s*");
                Object[] processedArgs = new Object[argArray.length];
                
                for (int i = 0; i < argArray.length; i++) {
                    processedArgs[i] = processExpression(argArray[i], context);
                }
                
                try {
                    String functionResult = function.execute(processedArgs, context);
                    matcher.appendReplacement(result, Matcher.quoteReplacement(functionResult));
                    context.addVariableUsed(functionName + "()");
                } catch (Exception e) {
                    Log.w(TAG, "Function execution failed: " + functionName, e);
                    matcher.appendReplacement(result, "[ERROR: " + functionName + "]");
                }
            } else {
                matcher.appendReplacement(result, "[UNKNOWN: " + functionName + "]");
            }
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    /**
     * Process template loops
     */
    private String processLoops(String template, TemplateContext context) {
        Matcher matcher = LOOP_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String variableName = matcher.group(1).trim();
            String collectionName = matcher.group(2).trim();
            String loopContent = matcher.group(3);
            
            try {
                Object collection = context.getVariable(collectionName);
                StringBuilder loopResult = new StringBuilder();
                
                if (collection instanceof List) {
                    List<?> list = (List<?>) collection;
                    for (Object item : list) {
                        context.pushScope(variableName, item);
                        String processedContent = processTemplate(loopContent, context);
                        loopResult.append(processedContent);
                        context.popScope();
                    }
                } else if (collection instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) collection;
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        context.pushScope("key", entry.getKey());
                        context.pushScope("value", entry.getValue());
                        String processedContent = processTemplate(loopContent, context);
                        loopResult.append(processedContent);
                        context.popScope();
                        context.popScope();
                    }
                } else {
                    Log.w(TAG, "Invalid collection for loop: " + collectionName);
                }
                
                matcher.appendReplacement(result, Matcher.quoteReplacement(loopResult.toString()));
                context.addVariableUsed(collectionName);
                
            } catch (Exception e) {
                Log.e(TAG, "Loop processing failed", e);
                matcher.appendReplacement(result, "[LOOP ERROR]");
            }
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    /**
     * Process template conditionals
     */
    private String processConditionals(String template, TemplateContext context) {
        Matcher matcher = CONDITIONAL_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String condition = matcher.group(1).trim();
            String content = matcher.group(2);
            
            try {
                if (evaluateCondition(condition, context)) {
                    matcher.appendReplacement(result, Matcher.quoteReplacement(processTemplate(content, context)));
                } else {
                    matcher.appendReplacement(result, "");
                }
            } catch (Exception e) {
                Log.e(TAG, "Conditional processing failed", e);
                matcher.appendReplacement(result, "[CONDITIONAL ERROR]");
            }
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    /**
     * Process template variables
     */
    private String processVariables(String template, TemplateContext context) {
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String variableName = matcher.group(1).trim();
            
            try {
                Object value = processExpression(variableName, context);
                String stringValue = value != null ? value.toString() : "";
                matcher.appendReplacement(result, Matcher.quoteReplacement(stringValue));
                context.addVariableUsed(variableName);
            } catch (Exception e) {
                Log.w(TAG, "Variable processing failed: " + variableName, e);
                matcher.appendReplacement(result, "[ERROR: " + variableName + "]");
            }
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    /**
     * Process expression (variable or literal)
     */
    private Object processExpression(String expression, TemplateContext context) {
        expression = expression.trim();
        
        // Check if it's a literal string
        if (expression.startsWith("\"") && expression.endsWith("\"")) {
            return expression.substring(1, expression.length() - 1);
        }
        
        // Check if it's a literal number
        if (expression.matches("-?\\d+(\\.\\d+)?")) {
            try {
                if (expression.contains(".")) {
                    return Double.parseDouble(expression);
                } else {
                    return Integer.parseInt(expression);
                }
            } catch (NumberFormatException e) {
                return expression;
            }
        }
        
        // Treat as variable
        return context.getVariable(expression);
    }
    
    /**
     * Evaluate condition
     */
    private boolean evaluateCondition(String condition, TemplateContext context) {
        try {
            // Simple condition evaluation
            if (condition.contains("==")) {
                String[] parts = condition.split("==");
                Object left = processExpression(parts[0].trim(), context);
                Object right = processExpression(parts[1].trim(), context);
                return left != null && left.equals(right);
            } else if (condition.contains("!=")) {
                String[] parts = condition.split("!=");
                Object left = processExpression(parts[0].trim(), context);
                Object right = processExpression(parts[1].trim(), context);
                return !left.equals(right);
            } else {
                // Single variable condition
                Object value = processExpression(condition, context);
                if (value instanceof Boolean) {
                    return (Boolean) value;
                } else if (value instanceof String) {
                    return !((String) value).isEmpty();
                } else if (value instanceof Number) {
                    return ((Number) value).doubleValue() != 0;
                }
                return value != null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Condition evaluation failed: " + condition, e);
            return false;
        }
    }
    
    /**
     * Create template context from recipient
     */
    private TemplateContext createTemplateContext(Recipient recipient) {
        TemplateContext context = new TemplateContext();
        
        // Add recipient variables
        context.setVariable("name", recipient.getName());
        context.setVariable("phone", recipient.getPhone());
        context.setVariable("amount", recipient.getAmount());
        
        // Add recipient fields
        if (recipient.getFields() != null) {
            for (Map.Entry<String, String> entry : recipient.getFields().entrySet()) {
                context.setVariable(entry.getKey(), entry.getValue());
            }
        }
        
        // Add system variables
        context.setVariable("date", new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));
        context.setVariable("time", new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        context.setVariable("datetime", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date()));
        
        return context;
    }
    
    /**
     * Initialize built-in functions
     */
    private Map<String, TemplateFunction> initializeFunctions() {
        Map<String, TemplateFunction> functions = new HashMap<>();
        
        // String functions
        functions.put("upper", (args, context) -> args.length > 0 ? args[0].toString().toUpperCase() : "");
        functions.put("lower", (args, context) -> args.length > 0 ? args[0].toString().toLowerCase() : "");
        functions.put("capitalize", (args, context) -> {
            if (args.length > 0) {
                String str = args[0].toString();
                if (!str.isEmpty()) {
                    return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
                }
            }
            return "";
        });
        functions.put("length", (args, context) -> String.valueOf(args.length > 0 ? args[0].toString().length() : 0));
        
        // Number functions
        functions.put("currency", (args, context) -> {
            if (args.length > 0 && args[0] instanceof Number) {
                double value = ((Number) args[0]).doubleValue();
                return String.format("$%.2f", value);
            }
            return "$0.00";
        });
        functions.put("percent", (args, context) -> {
            if (args.length > 0 && args[0] instanceof Number) {
                double value = ((Number) args[0]).doubleValue();
                return String.format("%.1f%%", value);
            }
            return "0.0%";
        });
        
        // Date functions
        functions.put("format_date", (args, context) -> {
            if (args.length >= 2) {
                try {
                    long timestamp = Long.parseLong(args[0].toString());
                    String format = args[1].toString();
                    return new SimpleDateFormat(format, Locale.getDefault()).format(new Date(timestamp));
                } catch (Exception e) {
                    return args[0].toString();
                }
            }
            return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        });
        
        // Conditional functions
        functions.put("default", (args, context) -> {
            if (args.length >= 2) {
                Object value = args[0];
                Object defaultValue = args[1];
                return String.valueOf((value != null && !value.toString().isEmpty()) ? value : defaultValue);
            }
            return "";
        });
        
        return functions;
    }
    
    // Helper methods
    private List<String> extractVariables(String template) {
        List<String> variables = new ArrayList<>();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        while (matcher.find()) {
            variables.add(matcher.group(1).trim());
        }
        return variables;
    }
    
    private List<String> extractFunctionCalls(String template) {
        List<String> functions = new ArrayList<>();
        Matcher matcher = FUNCTION_PATTERN.matcher(template);
        while (matcher.find()) {
            functions.add(matcher.group(0));
        }
        return functions;
    }
    
    private String extractFunctionName(String functionCall) {
        Matcher matcher = Pattern.compile("^\\{\\{([^\\(]+)").matcher(functionCall);
        return matcher.find() ? matcher.group(1).trim() : "";
    }
    
    private boolean areTagsBalanced(String template) {
        // Simple tag balance check
        int ifCount = template.split("\\{\\%if").length - 1;
        int endifCount = template.split("\\{\\%endif").length - 1;
        int forCount = template.split("\\{\\%for").length - 1;
        int endforCount = template.split("\\{\\%endfor").length - 1;
        
        return ifCount == endifCount && forCount == endforCount;
    }
    
    private boolean isValidVariableName(String variable) {
        return variable.matches("[a-zA-Z_][a-zA-Z0-9_]*");
    }
    
    private boolean hasNestedConditionals(String template) {
        // Check for nested if statements
        int maxDepth = 0;
        int currentDepth = 0;
        
        for (int i = 0; i < template.length(); i++) {
            if (template.startsWith("{%if", i)) {
                currentDepth++;
                maxDepth = Math.max(maxDepth, currentDepth);
            } else if (template.startsWith("{%endif", i)) {
                currentDepth--;
            }
        }
        
        return maxDepth > 1;
    }
    
    private TemplateValidation validateTemplate(String rendered) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Check length
        if (rendered.length() > 1600) {
            errors.add("Message too long (max 1600 characters)");
        } else if (rendered.length() > 480) {
            warnings.add("Message is long, may be split into multiple parts");
        }
        
        // Check for empty placeholders
        if (rendered.contains("[ERROR:") || rendered.contains("[UNKNOWN:")) {
            errors.add("Template contains unresolved variables or functions");
        }
        
        return new TemplateValidation(errors.isEmpty(), errors, warnings);
    }
    
    /**
     * Template function interface
     */
    @FunctionalInterface
    public interface TemplateFunction {
        String execute(Object[] args, TemplateContext context) throws Exception;
    }
    
    /**
     * Template context for variable storage
     */
    public static class TemplateContext {
        private final Map<String, Object> variables = new HashMap<>();
        private final Map<String, Object> scope = new HashMap<>();
        private final List<String> variablesUsed = new ArrayList<>();
        
        public void setVariable(String name, Object value) {
            variables.put(name, value);
        }
        
        public Object getVariable(String name) {
            // Check scope first, then global variables
            if (scope.containsKey(name)) {
                return scope.get(name);
            }
            return variables.get(name);
        }
        
        public void pushScope(String name, Object value) {
            scope.put(name, value);
        }
        
        public void popScope() {
            // Remove last added scope variable (simplified)
            if (!scope.isEmpty()) {
                String lastKey = null;
                for (String key : scope.keySet()) {
                    lastKey = key;
                }
                if (lastKey != null) {
                    scope.remove(lastKey);
                }
            }
        }
        
        public void addVariableUsed(String variable) {
            if (!variablesUsed.contains(variable)) {
                variablesUsed.add(variable);
            }
        }
        
        public List<String> getVariablesUsed() {
            return new ArrayList<>(variablesUsed);
        }
    }
    
    /**
     * Template result
     */
    public static class TemplateResult {
        public final String renderedContent;
        public final TemplateValidation validation;
        public final long processingTimeMs;
        public final List<String> variablesUsed;
        
        public TemplateResult(String renderedContent, TemplateValidation validation, 
                           long processingTimeMs, List<String> variablesUsed) {
            this.renderedContent = renderedContent;
            this.validation = validation;
            this.processingTimeMs = processingTimeMs;
            this.variablesUsed = variablesUsed;
        }
    }
    
    /**
     * Template validation result
     */
    public static class TemplateValidation {
        public final boolean isValid;
        public final List<String> errors;
        public final List<String> warnings;
        
        public TemplateValidation(boolean isValid, List<String> errors, List<String> warnings) {
            this.isValid = isValid;
            this.errors = new ArrayList<>(errors);
            this.warnings = new ArrayList<>(warnings);
        }
    }
}
