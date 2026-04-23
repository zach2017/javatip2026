package com.example.audit.action;

import java.text.MessageFormat;
import java.util.Arrays;

/**
 * Package-private helper that applies format strings against method arguments,
 * a result, and/or an error. Supports:
 *   - {0}, {1}, ... -> method argument at that index (via MessageFormat)
 *   - {result}      -> returned value's toString
 *   - {error}       -> thrown Throwable's message
 */
final class AuditFormatter {

    private AuditFormatter() {}

    static String format(String template, Object[] args, Object result, Throwable error) {
        String withTokens = template
                .replace("{result}", result == null ? "" : String.valueOf(result))
                .replace("{error}", error == null ? "" : String.valueOf(error.getMessage()));
        try {
            Object[] safeArgs = args == null ? new Object[0] : args;
            return MessageFormat.format(withTokens, safeArgs);
        } catch (IllegalArgumentException e) {
            // Placeholders didn't match the argument list - fall back to best-effort output.
            return withTokens + " args=" + Arrays.toString(args);
        }
    }
}
