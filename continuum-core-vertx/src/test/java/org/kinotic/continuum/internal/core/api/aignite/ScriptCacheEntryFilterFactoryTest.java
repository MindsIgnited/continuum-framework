package org.kinotic.continuum.internal.core.api.aignite;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryEventFilter;
import javax.cache.event.CacheEntryListenerException;
import javax.cache.event.EventType;

import org.junit.jupiter.api.Test;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyObject;

/**
 * The remote filter of a continuous query is a SQL-style where clause that continuum rewrites into a
 * Groovy script and compiles on the Ignite node at runtime. Nothing else exercises that compiler, so
 * this pins the rewrite, the compile, and the evaluation against the Groovy the build resolves.
 */
class ScriptCacheEntryFilterFactoryTest {

    private static final Class<?> WIDGET = new GroovyClassLoader(ScriptCacheEntryFilterFactoryTest.class.getClassLoader())
            .parseClass("class Widget { String name; int count; String status }");

    @Test
    void compilesAndEvaluatesASqlStyleFilter() {
        CacheEntryEventFilter<String, Object> filter = filter("count > ? and status = 'active'", 3);

        assertTrue(filter.evaluate(event("w-1", widget("widget", 5, "active"))));
        assertFalse(filter.evaluate(event("w-1", widget("widget", 2, "active"))), "count too low");
        assertFalse(filter.evaluate(event("w-1", widget("widget", 5, "retired"))), "status differs");
    }

    @Test
    void likeBecomesARegexMatch() {
        CacheEntryEventFilter<String, Object> filter = filter("name like ?", "wid%");

        assertTrue(filter.evaluate(event("w-1", widget("widget", 1, "active"))));
        assertFalse(filter.evaluate(event("w-1", widget("gadget", 1, "active"))));
    }

    @Test
    void theCacheKeyIsVisibleAsIdentifier() {
        CacheEntryEventFilter<String, Object> filter = filter("identifier = 'w-1'");

        assertTrue(filter.evaluate(event("w-1", widget("widget", 1, "active"))));
        assertFalse(filter.evaluate(event("w-2", widget("widget", 1, "active"))));
    }

    @Test
    void orderByIsDroppedFromTheFilter() {
        CacheEntryEventFilter<String, Object> filter = filter("count > ? order by count asc", 1);

        assertTrue(filter.evaluate(event("w-1", widget("widget", 5, "active"))));
    }

    @Test
    void aFilterThatDoesNotYieldABooleanIsRejected() {
        CacheEntryEventFilter<String, Object> filter = filter("count");

        assertThrows(CacheEntryListenerException.class, () -> filter.evaluate(event("w-1", widget("widget", 5, "active"))));
    }

    @Test
    void theCompiledFilterIsReusedByTheFactory() {
        ScriptCacheEntryFilterFactory<String, Object> factory = new ScriptCacheEntryFilterFactory<>("count > ?", 1);

        assertSame(factory.create(), factory.create());
    }

    private static CacheEntryEventFilter<String, Object> filter(String sql, Object... args) {
        return new ScriptCacheEntryFilterFactory<String, Object>(sql, args).create();
    }

    private static Object widget(String name, int count, String status) {
        try {
            GroovyObject widget = (GroovyObject) WIDGET.getDeclaredConstructor().newInstance();
            widget.setProperty("name", name);
            widget.setProperty("count", count);
            widget.setProperty("status", status);
            return widget;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static CacheEntryEvent<String, Object> event(String key, Object value) {
        CacheEntryEvent<String, Object> event = mock(CacheEntryEvent.class);
        when(event.getEventType()).thenReturn(EventType.UPDATED);
        when(event.getKey()).thenReturn(key);
        when(event.getValue()).thenReturn(value);
        return event;
    }
}
