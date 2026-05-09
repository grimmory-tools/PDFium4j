import java.util.function.Supplier;

public class TestStableValue {
    public static void main(String[] args) {
        Supplier<String> s = StableValue.supplier(() -> "hello");
        System.out.println(s.get());
    }
}
