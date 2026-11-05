package com.schoolerp.shared.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.schoolerp.shared.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void blocksLoginRequestsExceedingPerMinuteThreshold() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(2);
        TenantContext.set("greenwood");

        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            req.setRemoteAddr("192.0.2.10");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, new MockFilterChain());
            assertEquals(200, res.getStatus());
        }

        MockHttpServletRequest blockedReq = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        blockedReq.setRemoteAddr("192.0.2.10");
        MockHttpServletResponse blockedRes = new MockHttpServletResponse();
        filter.doFilter(blockedReq, blockedRes, new MockFilterChain());
        assertEquals(429, blockedRes.getStatus());
        assertEquals("application/problem+json;charset=UTF-8", blockedRes.getContentType());
    }
}

