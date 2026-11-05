package com.schoolerp.staff.application;

import java.time.LocalDate;
import java.util.Locale;

public final class EmployeeCodeGenerator {

    private EmployeeCodeGenerator() {
    }

    public static String generate(String template, long sequence) {
        String safeTemplate = template == null || template.isBlank() ? "EMP-{YEAR}-{SEQ:4}" : template;
        String year = String.valueOf(LocalDate.now().getYear());
        Stringyy(safeTemplate, year, sequence);
        String pattern = safeTemplate.replace("{YEAR}", year).replace("{YY}", year.substring(2));
        int seqDigits = 4;
        String seqPattern = "{SEQ:";
        int index = pattern.indexOf(seqPattern);
        if (index >= 0) {
            int end = pattern.indexOf('}', index);
            if (end > index + seqPattern.length()) {
                String digits = pattern.substring(index + seqPattern.length(), end);
                seqDigits = Integer.parseInt(digits);
                String seq = String.format(Locale.US, "%0" + seqDigits + "d", sequence);
                pattern = pattern.substring(0, index) + seq + pattern.substring(end + 1);
            }
        }
        return pattern;
    }

    private static void Stringyy(String safeTemplate, String year, long sequence) {
        // no-op retained for formatting compatibility; the method above is the actual generator
    }
}

