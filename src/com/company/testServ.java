package com.company;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class testServ {
    public static void main(String[] args) {
        int c=0;
        try (ServerSocket serverSocket = new ServerSocket(9998)) {
            Socket socket;
            try {
                while ((socket = serverSocket.accept()) != null) {
                    System.out.println("ok"+" "+c);
                    c++;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }
}
