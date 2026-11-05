package com.schoolerp.notifications.application;

public class SmsSegmentCalculator {

    public static int countSegments(String text) {
        if (text == null || text.isBlank()) {
            return 1;
        }
        if (containsNonAscii(text)) {
            return calculateUnicodeSegments(text);
        }
        return calculateAsciiSegments(text);
    }

    private static int calculateAsciiSegments(String text) {
        int length = text.length();
        if (length <= 160) {
            return 1;
        }
        int segments = (length + 152) / 153;
        return Math.max(1, segments);
    }

    private static int calculateUnicodeSegments(String text) {
        int length = text.codePointCount(0, text.length());
        if (length <= 70) {
            return 1;
        }
        int segments = (length + 66) / 67;
        return Math.max(1, segments);
    }

    private static boolean containsNonAscii(String text) {
        for (int i = 0; i < text.length(); i++) {
            int codePoint = text.codePointAt(i);
            if (codePoint > 0x7F) {
                return true;
            }
        }
        return false;
    }
}
