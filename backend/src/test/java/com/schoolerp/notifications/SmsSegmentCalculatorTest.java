package com.schoolerp.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.schoolerp.notifications.application.SmsSegmentCalculator;
import org.junit.jupiter.api.Test;

class SmsSegmentCalculatorTest {

    @Test
    void countsAsciiSegments() {
        assertEquals(1, SmsSegmentCalculator.countSegments("Hello world"));
        assertEquals(2, SmsSegmentCalculator.countSegments("A".repeat(200)));
    }

    @Test
    void countsBanglaUnicodeSegments() {
        assertEquals(1, SmsSegmentCalculator.countSegments("ভালো বিশ্ব"));
        assertEquals(3, SmsSegmentCalculator.countSegments("ভালো".repeat(40)));
    }
}
