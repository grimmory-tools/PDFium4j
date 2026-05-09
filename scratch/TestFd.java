import java.io.FileOutputStream;
import java.io.FileDescriptor;
import java.lang.reflect.Field;

public class TestFd {
    public static void main(String[] args) throws Exception {
        FileOutputStream fos = new FileOutputStream("test.txt");
        FileDescriptor fd = fos.getFD();
        Field field = FileDescriptor.class.getDeclaredField("fd");
        field.setAccessible(true);
        int fdValue = (int) field.get(fd);
        System.out.println("FD: " + fdValue);
        fos.close();
    }
}
