import java.lang.foreign.*;
import java.util.*;

public class CheckLayouts {
    public static void main(String[] args) {
        Linker linker = Linker.nativeLinker();
        Map<String, MemoryLayout> layouts = linker.canonicalLayouts();
        System.out.println("int: " + layouts.get("int"));
        System.out.println("long: " + layouts.get("long"));
        System.out.println("pointer: " + layouts.get("pointer"));
    }
}
