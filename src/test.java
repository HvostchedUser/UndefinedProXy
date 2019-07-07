import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

public class test {
    public static void main(String[] args) throws IOException {
        Socket s=new Socket("example.com", 80);
        s.getOutputStream().write("GET / HTTP/1.1\r\nHost: www.example.com\r\n\r\n".getBytes());
        s.getOutputStream().flush();
        Scanner sc=new Scanner(s.getInputStream());
        while (true){
            System.out.println(sc.nextLine());
        }
    }
}
