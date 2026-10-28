package dev.monkeypatch.rctiming.security;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

/** The role annotations stand for the same {@code @PreAuthorize} rules that the controllers used to spell out. */
class RoleAnnotationsTest {

    @Test
    void eachRoleAnnotationIsThePreAuthorizeRuleItsNameSays() {
        assertThat(rule(AdminOnly.class)).isEqualTo("hasRole('ADMIN')");
        assertThat(rule(DirectorOrAdmin.class)).isEqualTo("hasAnyRole('ADMIN', 'RACE_DIRECTOR')");
        assertThat(rule(RaceControlStaff.class)).isEqualTo("hasAnyRole('ADMIN', 'RACE_DIRECTOR', 'REFEREE')");
        assertThat(rule(RefereeOrAdmin.class)).isEqualTo("hasAnyRole('ADMIN', 'REFEREE')");
        assertThat(rule(AnyOfficial.class)).isEqualTo("isAuthenticated()");
    }

    private static String rule(Class<?> annotation) {
        PreAuthorize merged = AnnotatedElementUtils.findMergedAnnotation(annotation, PreAuthorize.class);
        assertThat(merged).isNotNull();
        return merged.value();
    }
}
