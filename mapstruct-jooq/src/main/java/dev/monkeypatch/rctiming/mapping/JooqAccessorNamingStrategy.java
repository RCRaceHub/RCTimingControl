package dev.monkeypatch.rctiming.mapping;

import org.mapstruct.ap.spi.DefaultAccessorNamingStrategy;

import javax.lang.model.element.ExecutableElement;

/**
 * Tells MapStruct to treat only {@code setX(value)} methods as setters. jOOQ's generated records also
 * have fluent methods such as {@code value1(String)} that return the record; MapStruct would take each
 * for a property and, with unmapped targets set to an error, refuse to build a mapper that fills a record.
 *
 * <p>Loaded by the Java compiler from the annotation processor path through
 * {@code META-INF/services}, so it applies to every mapper in {@code :app} at compile time only.
 */
public class JooqAccessorNamingStrategy extends DefaultAccessorNamingStrategy {

    @Override
    public boolean isFluentSetter(ExecutableElement method) {
        return false;
    }
}
