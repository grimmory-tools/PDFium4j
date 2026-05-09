import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;

public class TestAlloc {
    public static void main(String[] args) {
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        
        long tid = Thread.currentThread().threadId();
        
        // Warmup
        for (int i = 0; i < 10000; i++) {
            bean.getThreadAllocatedBytes(tid);
        }
        
        long before = bean.getThreadAllocatedBytes(tid);
        for (int i = 0; i < 100; i++) {
            bean.getThreadAllocatedBytes(tid);
        }
        long after = bean.getThreadAllocatedBytes(tid);
        
        System.out.println("Delta after 100 calls: " + (after - before));
    }
}
