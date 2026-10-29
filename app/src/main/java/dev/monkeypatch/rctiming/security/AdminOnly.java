package dev.monkeypatch.rctiming.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Only an administrator. Put it on a controller class or method in place of a written-out
 * {@code @PreAuthorize("hasRole('ADMIN')")}, so each role set is spelled once. Spring Security reads it as that
 * {@link PreAuthorize}; a refusal is a 403 from {@code GlobalExceptionHandler}.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasRole('ADMIN')")
public @interface AdminOnly {
}
