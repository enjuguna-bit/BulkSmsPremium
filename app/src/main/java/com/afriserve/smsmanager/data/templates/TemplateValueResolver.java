package com.afriserve.smsmanager.data.templates;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves direct, calculated and formatted template values.
 * This class deliberately contains no Java expression evaluation: only a small,
 * whitelisted arithmetic grammar is accepted.
 */
public final class TemplateValueResolver {
    private static final Pattern DOUBLE_TOKEN = Pattern.compile("\\{\\{([^{}]+)\\}\\}");
    private static final Pattern DIRECT_TOKEN = Pattern.compile("(?<!\\{)\\{([A-Za-z][A-Za-z0-9_]*)\\}(?!\\})");
    private static final Pattern BINARY_TOKEN = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_]*)\\}\\s*([+\\-*/])\\s*\\{([A-Za-z][A-Za-z0-9_]*)\\}");
    private static final Pattern CONDITIONAL = Pattern.compile("\\{\\?if\\s+([^}]+)\\}(.*?)\\{\\?endif\\}", Pattern.DOTALL);

    private TemplateValueResolver() { }

    @NonNull
    public static String process(@NonNull String template, @Nullable Map<String, String> source) {
        Map<String, String> values = canonicalize(source);
        String result = template;

        result = processConditionals(result, values);

        Matcher expressionMatcher = DOUBLE_TOKEN.matcher(result);
        StringBuffer expressions = new StringBuffer();
        while (expressionMatcher.find()) {
            String replacement = evaluateExpression(expressionMatcher.group(1).trim(), values);
            expressionMatcher.appendReplacement(expressions, Matcher.quoteReplacement(replacement));
        }
        expressionMatcher.appendTail(expressions);
        result = expressions.toString();

        // Also support the simple syntax users naturally type: {amount_due}+{arrears}.
        boolean changed;
        do {
            changed = false;
            Matcher binaryMatcher = BINARY_TOKEN.matcher(result);
            StringBuffer binary = new StringBuffer();
            while (binaryMatcher.find()) {
                String replacement = evaluateBinary(binaryMatcher.group(1), binaryMatcher.group(2), binaryMatcher.group(3), values);
                if (replacement == null) {
                    replacement = binaryMatcher.group(0);
                } else {
                    changed = true;
                }
                binaryMatcher.appendReplacement(binary, Matcher.quoteReplacement(replacement));
            }
            binaryMatcher.appendTail(binary);
            result = binary.toString();
        } while (changed);

        Matcher directMatcher = DIRECT_TOKEN.matcher(result);
        StringBuffer direct = new StringBuffer();
        while (directMatcher.find()) {
            String key = normalizeKey(directMatcher.group(1));
            String value = values.get(key);
            directMatcher.appendReplacement(direct, Matcher.quoteReplacement(value == null ? directMatcher.group(0) : value));
        }
        directMatcher.appendTail(direct);
        return direct.toString();
    }

    @NonNull
    public static Map<String, String> canonicalize(@Nullable Map<String, String> source) {
        Map<String, String> values = new LinkedHashMap<>();
        if (source != null) {
            for (Map.Entry<String, String> entry : source.entrySet()) {
                String key = normalizeKey(entry.getKey());
                if (!key.isEmpty() && !values.containsKey(key)) {
                    values.put(key, entry.getValue() == null ? "" : entry.getValue());
                }
            }
        }

        addAlias(values, "total_due", first(values, "total_due", "amount_due"), false);
        if (has(values, "amount_due") || has(values, "arrears")) {
            String amountDue = has(values, "amount_due") ? values.get("amount_due") : "0";
            String arrears = has(values, "arrears") ? values.get("arrears") : "0";
            Map<String, String> totals = new LinkedHashMap<>();
            totals.put("amount_due", amountDue);
            totals.put("arrears", arrears);
            values.put("total_due", calculate(totals, "amount_due", "+", "arrears"));
            values.put("total_payable", values.get("total_due"));
        }
        if (has(values, "loan_balance")) {
            values.put("outstanding_balance", values.get("loan_balance"));
        } else if (has(values, "balance")) {
            values.put("outstanding_balance", values.get("balance"));
        }
        if (has(values, "arrears_amount")) {
            values.put("current_arrears", values.get("arrears_amount"));
        }
        return values;
    }

    @NonNull
    public static List<String> extractReferencedNames(@Nullable String template) {
        List<String> names = new ArrayList<>();
        if (template == null) return names;
        Matcher matcher = Pattern.compile("\\{\\{([^{}]+)\\}\\}|\\{([A-Za-z][A-Za-z0-9_]*)\\}").matcher(template);
        while (matcher.find()) {
            String content = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (content == null) continue;
            String name = content.trim();
            int paren = name.indexOf('(');
            if (paren > 0) name = name.substring(paren + 1, name.endsWith(")") ? name.length() - 1 : name.length()).trim();
            for (String part : name.split("[^A-Za-z0-9_]+")) {
                if (part.matches("[A-Za-z][A-Za-z0-9_]*") && !names.contains(part)) names.add(part);
            }
        }
        return names;
    }

    @NonNull
    public static String normalizeKey(@Nullable String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    private static String processConditionals(String template, Map<String, String> values) {
        String result = template;
        Matcher matcher = CONDITIONAL.matcher(result);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String body = matcher.group(2);
            String[] branches = body.split("\\{\\?else\\}", 2);
            String chosen = evaluateCondition(matcher.group(1).trim(), values)
                    ? branches[0]
                    : (branches.length > 1 ? branches[1] : "");
            matcher.appendReplacement(output, Matcher.quoteReplacement(chosen));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private static boolean evaluateCondition(String condition, Map<String, String> values) {
        Matcher comparison = Pattern.compile("^([A-Za-z][A-Za-z0-9_]*)\\s*(==|!=|>=|<=|>|<)\\s*(.+)$").matcher(condition);
        if (!comparison.matches()) return has(values, condition);
        String left = values.get(normalizeKey(comparison.group(1)));
        String right = comparison.group(3).trim().replaceAll("^\\\"|\\\"$", "");
        String op = comparison.group(2);
        BigDecimal leftNumber = parseNumber(left);
        BigDecimal rightNumber = parseNumber(right);
        int comparisonResult = leftNumber != null && rightNumber != null
                ? leftNumber.compareTo(rightNumber)
                : String.valueOf(left).compareToIgnoreCase(right);
        switch (op) {
            case "==": return comparisonResult == 0;
            case "!=": return comparisonResult != 0;
            case ">": return comparisonResult > 0;
            case "<": return comparisonResult < 0;
            case ">=": return comparisonResult >= 0;
            case "<=": return comparisonResult <= 0;
            default: return false;
        }
    }

    private static String evaluateExpression(String expression, Map<String, String> values) {
        Matcher function = Pattern.compile("^([A-Za-z][A-Za-z0-9_]*)\\((.*)\\)$").matcher(expression);
        if (function.matches()) {
            String name = function.group(1).toLowerCase(Locale.ROOT);
            List<String> args = splitArguments(function.group(2));
            if ("money".equals(name) && !args.isEmpty()) return money(evaluateArgument(args.get(0), values));
            if ("number".equals(name) && !args.isEmpty()) return number(evaluateArgument(args.get(0), values));
            if ("upper".equals(name) && !args.isEmpty()) return evaluateArgument(args.get(0), values).toUpperCase(Locale.ROOT);
            if ("lower".equals(name) && !args.isEmpty()) return evaluateArgument(args.get(0), values).toLowerCase(Locale.ROOT);
            if ("default".equals(name) && args.size() >= 2) {
                String value = evaluateArgument(args.get(0), values);
                return value.isEmpty() || value.startsWith("{") ? stripQuotes(args.get(1)) : value;
            }
            if ("date".equals(name) && !args.isEmpty()) return formatDate(evaluateArgument(args.get(0), values), args.size() > 1 ? stripQuotes(args.get(1)) : "dd MMM yyyy");
            return "{{" + expression + "}}";
        }
        String arithmetic = evaluateArithmetic(expression, values);
        return arithmetic == null ? String.valueOf(values.getOrDefault(normalizeKey(expression), "{{" + expression + "}}")) : arithmetic;
    }

    private static String evaluateArgument(String argument, Map<String, String> values) {
        String clean = argument.trim();
        if (clean.matches("[A-Za-z][A-Za-z0-9_]*")) return values.getOrDefault(normalizeKey(clean), "");
        String arithmetic = evaluateArithmetic(clean, values);
        return arithmetic != null ? arithmetic : stripQuotes(clean);
    }

    private static String evaluateBinary(String left, String operator, String right, Map<String, String> values) {
        return calculate(values, left, operator, right);
    }

    private static String evaluateArithmetic(String expression, Map<String, String> values) {
        String clean = expression.trim();
        if (!clean.matches("[A-Za-z0-9_.\\s+*/-]+")) return null;
        String[] parts = clean.split("\\s*([+\\-*/])\\s*");
        if (parts.length < 2) return null;
        BigDecimal result = operand(parts[0], values);
        if (result == null) return null;
        String[] operandParts = clean.split("\\s*[+\\-*/]\\s*");
        Matcher operatorMatcher = Pattern.compile("[+\\-*/]").matcher(clean);
        for (int index = 1; index < operandParts.length; index++) {
            if (!operatorMatcher.find()) return null;
            BigDecimal right = operand(operandParts[index], values);
            if (right == null) return null;
            String op = operatorMatcher.group();
            switch (op) {
                case "+": result = result.add(right); break;
                case "-": result = result.subtract(right); break;
                case "*": result = result.multiply(right); break;
                case "/": if (right.compareTo(BigDecimal.ZERO) == 0) return null; result = result.divide(right, 8, RoundingMode.HALF_UP); break;
                default: return null;
            }
        }
        return result.stripTrailingZeros().toPlainString();
    }

    private static String calculate(Map<String, String> values, String leftKey, String operator, String rightKey) {
        BigDecimal left = operand(leftKey, values);
        BigDecimal right = operand(rightKey, values);
        if (left == null || right == null) return null;
        try {
            BigDecimal result;
            switch (operator) {
                case "+": result = left.add(right); break;
                case "-": result = left.subtract(right); break;
                case "*": result = left.multiply(right); break;
                case "/": if (right.compareTo(BigDecimal.ZERO) == 0) return null; result = left.divide(right, 8, RoundingMode.HALF_UP); break;
                default: return null;
            }
            return result.stripTrailingZeros().toPlainString();
        } catch (ArithmeticException ignored) { return null; }
    }

    private static BigDecimal operand(String token, Map<String, String> values) {
        BigDecimal literal = parseNumber(token);
        return literal != null ? literal : parseNumber(values.get(normalizeKey(token)));
    }

    private static BigDecimal parseNumber(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String clean = value.replaceAll("(?i)ksh|kes|,", "").trim();
        try { return new BigDecimal(clean); } catch (NumberFormatException ignored) { return null; }
    }

    private static String money(String value) {
        BigDecimal number = parseNumber(value);
        return number == null ? value : String.format(Locale.US, "%,.2f", number.doubleValue());
    }

    private static String number(String value) {
        BigDecimal number = parseNumber(value);
        return number == null ? value : String.format(Locale.US, "%,.0f", number.doubleValue());
    }

    private static String formatDate(String value, String targetPattern) {
        if (value == null || value.trim().isEmpty()) return "";
        String[] inputs = {"MM/dd/yyyy hh:mm:ss a", "M/d/yyyy hh:mm:ss a", "MM/dd/yyyy", "M/d/yyyy", "yyyy-MM-dd"};
        for (String input : inputs) {
            try {
                Date date = new SimpleDateFormat(input, Locale.US).parse(value.trim());
                return new SimpleDateFormat(targetPattern, Locale.US).format(date);
            } catch (ParseException ignored) { }
        }
        return value;
    }

    private static List<String> splitArguments(String args) {
        List<String> result = new ArrayList<>();
        for (String arg : args.split(",")) result.add(arg.trim());
        return result;
    }

    private static String stripQuotes(String value) { return value.trim().replaceAll("^\\\"|\\\"$", ""); }
    private static boolean has(Map<String, String> values, String key) { return values.containsKey(normalizeKey(key)) && !values.get(normalizeKey(key)).trim().isEmpty(); }
    private static String first(Map<String, String> values, String... keys) { for (String key : keys) if (has(values, key)) return values.get(normalizeKey(key)); return ""; }
    private static void addAlias(Map<String, String> values, String key, String value, boolean overwrite) { if (overwrite || !values.containsKey(key)) values.put(key, value); }
}
