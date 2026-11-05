package com.schoolerp.shared.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

class PermissionCheckerTest {

    private final PermissionChecker checker = new PermissionChecker();

    private static Authentication authFor(SchoolUserPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    @Test
    void allScopeGrantSatisfiesGlobalAndScopedChecks() {
        SchoolUserPrincipal admin = new SchoolUserPrincipal(
                1L,
                "admin@school.test",
                "Admin",
                "school",
                Set.of("SCHOOL_ADMIN"),
                List.of(new ScopedPermission("student.read", ScopeType.ALL, 0L)));
        Authentication auth = authFor(admin);

        assertTrue(checker.has(auth, "student.read"));
        assertTrue(checker.hasScoped(auth, "student.read", "SECTION", 105L));
        assertTrue(checker.hasScoped(auth, "student.read", "DEPARTMENT", 9L));
        assertFalse(checker.has(auth, "student.write"));
    }

    @Test
    void sectionScopeGrantMatchesOnlyAssignedSection() {
        SchoolUserPrincipal teacher = new SchoolUserPrincipal(
                10L,
                "teacher@school.test",
                "Teacher",
                "school",
                Set.of("TEACHER"),
                List.of(new ScopedPermission("marks.write", ScopeType.SECTION, 105L)));
        Authentication auth = authFor(teacher);

        assertFalse(checker.has(auth, "marks.write"));
        assertTrue(checker.hasScoped(auth, "marks.write", "SECTION", 105L));
        assertFalse(checker.hasScoped(auth, "marks.write", "SECTION", 106L));
    }

    @Test
    void ownScopeGrantMatchesOnlyPrincipalUserId() {
        SchoolUserPrincipal student = new SchoolUserPrincipal(
                77L,
                "student@school.test",
                "Student",
                "school",
                Set.of("STUDENT"),
                List.of(new ScopedPermission("profile.read", ScopeType.OWN, 0L)));
        Authentication auth = authFor(student);

        assertTrue(checker.hasScoped(auth, "profile.read", "OWN", 77L));
        assertFalse(checker.hasScoped(auth, "profile.read", "OWN", 78L));
    }
}

