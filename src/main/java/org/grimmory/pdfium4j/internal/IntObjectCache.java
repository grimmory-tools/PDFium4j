package org.grimmory.pdfium4j.internal;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** A simple LRU cache for int keys to Object values, supporting a byte-budget eviction policy. */
public final class IntObjectCache<T> {
  private final long maxBytes;
  private long currentBytes;
  private final LinkedHashMap<Integer, Entry<T>> map;
  private final Consumer<T> onEvict;

  private record Entry<T>(T value, long size) {}

  public IntObjectCache(long maxBytes) {
    this(maxBytes, v -> {});
  }

  public IntObjectCache(long maxBytes, Consumer<T> onEvict) {
    if (maxBytes < 0) {
      throw new IllegalArgumentException("maxBytes must be >= 0");
    }
    this.maxBytes = maxBytes;
    this.onEvict = onEvict;
    this.currentBytes = 0;
    this.map = new LinkedHashMap<>(16, 0.75f, true);
  }

  public synchronized T get(int key) {
    Entry<T> entry = map.get(key);
    return entry != null ? entry.value : null;
  }

  public synchronized void put(int key, T value, long size) {
    if (size < 0) {
      throw new IllegalArgumentException("size must be >= 0");
    }
    if (size > maxBytes) {
      // Too large to ever fit; evict existing but don't store or evict the new one
      remove(key);
      return;
    }

    Entry<T> old = map.put(key, new Entry<>(value, size));
    if (old != null) {
      currentBytes -= old.size;
      onEvict.accept(old.value);
    }
    currentBytes += size;

    evictIfNecessary();
  }

  private void evictIfNecessary() {
    if (currentBytes <= maxBytes) return;

    Iterator<Map.Entry<Integer, Entry<T>>> it = map.entrySet().iterator();
    while (it.hasNext() && currentBytes > maxBytes) {
      Map.Entry<Integer, Entry<T>> entry = it.next();
      it.remove();
      currentBytes -= entry.getValue().size;
      onEvict.accept(entry.getValue().value);
    }
  }

  public synchronized void remove(int key) {
    Entry<T> entry = map.remove(key);
    if (entry != null) {
      currentBytes -= entry.size;
      onEvict.accept(entry.value);
    }
  }

  public synchronized void removeIf(int key, T value) {
    Entry<T> entry = map.get(key);
    if (entry != null && entry.value == value) {
      map.remove(key);
      currentBytes -= entry.size;
      onEvict.accept(entry.value);
    }
  }

  public synchronized void clear() {
    for (Entry<T> entry : map.values()) {
      onEvict.accept(entry.value);
    }
    map.clear();
    currentBytes = 0;
  }

  public synchronized long currentBytes() {
    return currentBytes;
  }
}
