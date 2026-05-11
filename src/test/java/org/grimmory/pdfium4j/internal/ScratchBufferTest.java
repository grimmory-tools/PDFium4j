package org.grimmory.pdfium4j.internal;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScratchBufferTest {

  @BeforeEach
  void setup() {
    ScratchBuffer.purge();
    ScratchBuffer.acquire();
  }

  @AfterEach
  void cleanup() {
    ScratchBuffer.release();
  }

  @Test
  void acquireScopeProvidesValidBuffer() {
    // Release the setup acquire to test scope isolation
    ScratchBuffer.release();
    try {
      assertThrows(IllegalStateException.class, () -> ScratchBuffer.get(8));

      try (var _ = ScratchBuffer.acquireScope()) {
        MemorySegment s = ScratchBuffer.get(8);
        assertTrue(s.byteSize() >= 8);
        s.set(JAVA_BYTE, 0, (byte) 0x11);
      }

      assertThrows(IllegalStateException.class, () -> ScratchBuffer.get(8));
    } finally {
      ScratchBuffer.acquire();
    }
  }

  @Test
  void acquireScopeIsReentrant() {
    try (var _ = ScratchBuffer.acquireScope()) {
      MemorySegment s1 = ScratchBuffer.get(8);
      s1.set(JAVA_BYTE, 0, (byte) 0x22);

      try (var _ = ScratchBuffer.acquireScope()) {
        MemorySegment s2 = ScratchBuffer.get(16);
        assertEquals((byte) 0x22, s1.get(JAVA_BYTE, 0));
        s2.set(JAVA_BYTE, 0, (byte) 0x33);
      }

      // Buffer should still be valid after inner scope closes
      assertEquals((byte) 0x33, ScratchBuffer.get(16).get(JAVA_BYTE, 0));
    }
  }

  @Test
  void utf8KeyAndWideValueUsesUtf8ByteOffset() {
    String key = "Ünî";
    long valueBytes = 32;

    ScratchBuffer.KeyValueSlots pair = ScratchBuffer.utf8KeyAndWideValue(key, valueBytes);

    MemorySegment scratch = MemorySegment.ofArray(new byte[32]);
    MemorySegment expectedKey = FfmHelper.writeUtf8String(scratch, key);

    assertEquals(expectedKey.byteSize(), pair.keySeg().byteSize());
    assertEquals(valueBytes, pair.valueSeg().byteSize());

    long keyTerminatorOffset = pair.keySeg().byteSize() - 1;
    assertEquals(0, pair.keySeg().get(JAVA_BYTE, keyTerminatorOffset));
    pair.valueSeg().set(JAVA_BYTE, 0, (byte) 0x7F);
    assertEquals(0, pair.keySeg().get(JAVA_BYTE, keyTerminatorOffset));
  }

  @Test
  void shrinksAfterOversizedRequest() {
    // Must exceed STEADY_STATE_SIZE (16MB) to trigger revert-to-steady on next small request
    ScratchBuffer.get(20L * 1024L * 1024L);
    long largeCapacity = ScratchBuffer.currentCapacity();
    assertTrue(largeCapacity >= 20L * 1024L * 1024L);

    ScratchBuffer.get(8);
    long shrunkCapacity = ScratchBuffer.currentCapacity();
    assertTrue(shrunkCapacity < largeCapacity);
    assertTrue(shrunkCapacity >= 8);
    assertTrue(shrunkCapacity <= 16L * 1024L * 1024L);
  }

  @Test
  void utf8ProbeBufferClampsToMax() {
    long maxSize = 1024L * 1024L * 256L; // matches ScratchBuffer.MAX_SIZE
    assertEquals(maxSize, ScratchBuffer.probeSize(maxSize));
    assertEquals(maxSize, ScratchBuffer.probeSize(Long.MAX_VALUE));
  }

  @Test
  void oscillatingRequestsReuseLargestSegment() {
    MemorySegment large = ScratchBuffer.get(10L * 1024L * 1024L);

    ScratchBuffer.get(8);
    MemorySegment medium = ScratchBuffer.get(5L * 1024L * 1024L);

    assertSame(large, medium);
  }

  @Test
  void keyAndWideValueRequiresAcquire() {
    ScratchBuffer.release();
    try {
      MemorySegment key = MemorySegment.ofArray(new byte[8]);
      MemorySegment value = MemorySegment.ofArray(new byte[8]);
      assertThrows(IllegalStateException.class, () -> ScratchBuffer.keyAndWideValue(key, value));
    } finally {
      ScratchBuffer.acquire();
    }
  }

  @Test
  void rejectsSizesAboveSafetyLimit() {
    assertThrows(IllegalArgumentException.class, () -> ScratchBuffer.get(256L * 1024L * 1024L + 1));
  }

  @Test
  void purgeClosesAllArenas() {
    MemorySegment first = ScratchBuffer.get(4096);
    ScratchBuffer.get(2L * 1024L * 1024L); // force grow -> new arena

    ScratchBuffer.purge();

    // both arenas should now be closed
    assertThrows(IllegalStateException.class, () -> first.get(JAVA_BYTE, 0));

    // Restore state for cleanup()
    ScratchBuffer.acquire();
  }

  @Test
  void growthDoesNotInvalidateOldSegment() {
    MemorySegment first = ScratchBuffer.get(4096);
    first.set(JAVA_BYTE, 0, (byte) 0x5A);
    MemorySegment second = ScratchBuffer.get(2L * 1024L * 1024L);
    second.set(JAVA_BYTE, 0, (byte) 0x2A);

    // Previously returned segment should still be valid because ScratchBuffer
    // now keeps a list of arenas until release().
    assertEquals((byte) 0x5A, first.get(JAVA_BYTE, 0));
  }

  @Test
  void threadIsolationProvidesDistinctAddresses() throws InterruptedException {
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch go = new CountDownLatch(1);
    AtomicLong address1 = new AtomicLong();
    AtomicLong address2 = new AtomicLong();

    Thread t1 =
        new Thread(
            () -> {
              try {
                ScratchBuffer.acquire();
                MemorySegment s = ScratchBuffer.get(64);
                address1.set(s.address());
                ready.countDown();
                go.await();
              } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
              } finally {
                ScratchBuffer.release();
              }
            });
    Thread t2 =
        new Thread(
            () -> {
              try {
                ScratchBuffer.acquire();
                MemorySegment s = ScratchBuffer.get(64);
                address2.set(s.address());
                ready.countDown();
                go.await();
              } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
              } finally {
                ScratchBuffer.release();
              }
            });

    t1.start();
    t2.start();
    ready.await();
    go.countDown();
    t1.join();
    t2.join();

    assertNotEquals(0, address1.get());
    assertNotEquals(0, address2.get());
    assertNotEquals(address1.get(), address2.get());
  }

  @Test
  void callWithScratchReturnsResult() throws Exception {
    String result =
        ScratchBuffer.callWithScratch(
            () -> {
              ScratchBuffer.get(8);
              return "success";
            });
    assertEquals("success", result);
  }
}
