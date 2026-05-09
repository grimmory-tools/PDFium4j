package org.grimmory.pdfium4j.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class IntObjectCacheTest {

  @Test
  void testByteBudgetEviction() {
    AtomicInteger evictCount = new AtomicInteger(0);
    IntObjectCache<String> cache = new IntObjectCache<>(100, value -> evictCount.incrementAndGet());

    cache.put(1, "A", 40);
    cache.put(2, "B", 40);
    assertEquals(80, cache.currentBytes());
    assertEquals(0, evictCount.get());

    cache.put(3, "C", 40); // Total 120, should evict "A"
    assertEquals(80, cache.currentBytes());
    assertEquals(1, evictCount.get());
    assertNull(cache.get(1));
    assertEquals("B", cache.get(2));
    assertEquals("C", cache.get(3));
  }

  @Test
  void testLargeObjectEviction() {
    IntObjectCache<String> cache = new IntObjectCache<>(100);
    cache.put(1, "Large", 150); // Should not be added
    assertEquals(0, cache.currentBytes());
    assertNull(cache.get(1));
  }

  @Test
  void testReplacementUpdateSize() {
    IntObjectCache<String> cache = new IntObjectCache<>(100);
    cache.put(1, "A", 40);
    cache.put(1, "A-updated", 60);
    assertEquals(60, cache.currentBytes());
    assertEquals("A-updated", cache.get(1));
  }
}
