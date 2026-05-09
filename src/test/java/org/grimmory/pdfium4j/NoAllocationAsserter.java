package org.grimmory.pdfium4j;

import static org.junit.jupiter.api.Assertions.fail;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;

public final class NoAllocationAsserter {

  private final ThreadMXBean threadMxBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();

  private Thread testThread;
  private long allocatedBytesBefore;

  public void verifyAllocationTrackingAvailable() {
    if (!threadMxBean.isThreadAllocatedMemorySupported()) {
      throw new IllegalStateException("Thread allocation tracking is not supported by this JVM");
    }
    if (!threadMxBean.isThreadAllocatedMemoryEnabled()) {
      threadMxBean.setThreadAllocatedMemoryEnabled(true);
    }
  }

  public void startRecording() {
    testThread = Thread.currentThread();
    allocatedBytesBefore = threadMxBean.getThreadAllocatedBytes(testThread.threadId());
  }

  public void assertNoAllocations(long tolerance) {
    long allocatedBytesAfter = threadMxBean.getThreadAllocatedBytes(testThread.threadId());
    long delta = allocatedBytesAfter - allocatedBytesBefore;
    testThread = null;
    if (delta > tolerance) {
      System.out.println(
          "ALLOCATION FAILURE: Observed " + delta + " bytes (tolerance " + tolerance + ")");
      fail(
          "Expected zero thread allocations (tolerance: "
              + tolerance
              + ") but observed "
              + delta
              + " allocated bytes");
    }
  }
}
