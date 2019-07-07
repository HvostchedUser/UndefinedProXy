import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

public class test {
    public static void main(String[] args) throws IOException {
        long beg=System.currentTimeMillis();
        int count=0;
        while (System.currentTimeMillis()<=1000+beg) {
            Socket s = new Socket("127.0.0.1", 9999);
            s.getOutputStream().write("GET / HTTP/1.1\r\nHost: www.example.com\r\n\r\n".getBytes());
            s.getOutputStream().flush();
            count++;
        }
        System.out.println(count);
    }
}
