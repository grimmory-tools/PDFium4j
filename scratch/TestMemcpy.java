import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

public class TestMemcpy {
    public static void main(String[] args) throws Throwable {
        Linker linker = Linker.nativeLinker();
        SymbolLookup libc = linker.defaultLookup();
        MethodHandle memcpy = libc.find("memcpy").map(addr -> linker.downcallHandle(addr, 
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG))).orElse(null);
        
        if (memcpy == null) {
            System.out.println("memcpy not found");
            return;
        }
        
        byte[] dest = new byte[10];
        MemorySegment destSeg = MemorySegment.ofArray(dest);
        
        byte[] src = new byte[]{1, 2, 3, 4, 5};
        MemorySegment srcSeg = MemorySegment.ofArray(src);
        
        MemorySegment ret = (MemorySegment) memcpy.invokeExact(destSeg, srcSeg, 5L);
        
        for (int i = 0; i < 5; i++) {
            System.out.print(dest[i] + " ");
        }
        System.out.println();
    }
}
