package com.company;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServerOld extends Thread {
    SQLCONN sqlconn=new SQLCONN("jdbc:postgresql://ec2-54-246-84-100.eu-west-1.compute.amazonaws.com:5432/df1akdqbsk9ssm","pzkrduwxzmqirf","29ac0888c17302618b3ffd26971c1022481cc5a2602cae11e0e7aaf68595a593");
    public static void main(String[] args) throws SQLException {
        (new ServerOld()).run();
    }

    public ServerOld() throws SQLException {

        super("Server Thread");
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(9999)) {
            Socket socket;
            try {
                while ((socket = serverSocket.accept()) != null) {
                    String s=socket.getInetAddress().toString().substring(1);
                    System.out.println("User connected: "+s);
                    ResultSet rs=sqlconn.request("SELECT unit_id FROM computers WHERE ip = '" + s + "'");
                    String id = "";
                    while (rs.next()) {
                        id = rs.getString("unit_id");
                        System.out.println("User unit id: "+id);
                    }
                    (new Handler(sqlconn,socket,id)).start();
                }
            } catch (IOException e) {
                e.printStackTrace();  // TODO: implement catch
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();  // TODO: implement catch
            return;
        }
    }

    public static class Handler extends Thread {
        String unitid="";
        String[] list;
        String[] reason;
        SQLCONN sqlconn;
        public static final Pattern CONNECT_PATTERN = Pattern.compile("CONNECT (.+):(.+) HTTP/(1\\.[01])", Pattern.CASE_INSENSITIVE);
        private final Socket clientSocket;
        private boolean previousWasR = false;

        public Handler(SQLCONN sqlconn, Socket clientSocket, String id) throws SQLException {
            this.sqlconn=sqlconn;
            this.unitid=id;
            this.clientSocket = clientSocket;
            ArrayList<String> bl=new ArrayList<>();
            ArrayList<String> re=new ArrayList<>();
            ResultSet rs=sqlconn.request("SELECT * FROM black_list WHERE id IN (SELECT address_id FROM jopa WHERE unit_id = '"+unitid+"')");
            while(rs.next()){
                bl.add(rs.getString("address"));
                re.add(rs.getString("reason"));
                //System.out.println(rs.getString("address")+": "+rs.getString("reason"));
            }
            list=bl.toArray(new String[0]);
            reason=re.toArray(new String[0]);
        }

        @Override
        public void run() {
            try {
                String request = readLine(clientSocket);
                System.out.println("Got request: "+request);

                Matcher matcher = CONNECT_PATTERN.matcher(request);
                if (matcher.matches()) {
                    String header;
                    do {
                        header = readLine(clientSocket);
                    } while (!"".equals(header));
                    OutputStreamWriter outputStreamWriter = new OutputStreamWriter(clientSocket.getOutputStream(),
                            "ISO-8859-1");

                    final Socket forwardSocket;
                    try {
                        forwardSocket = new Socket(matcher.group(1), Integer.parseInt(matcher.group(2)));
                        System.out.println(forwardSocket);
                    } catch (IOException | NumberFormatException e) {
                        e.printStackTrace();  // TODO: implement catch
                        outputStreamWriter.write("HTTP/" + matcher.group(3) + " 502 Bad Gateway\r\n");
                        outputStreamWriter.write("Proxy-agent: UndefinedProxy1.0\r\n");
                        outputStreamWriter.write("\r\n");
                        outputStreamWriter.flush();
                        return;
                    }
                    try {
                        outputStreamWriter.write("HTTP/" + matcher.group(3) + " 200 Connection established\r\n");
                        outputStreamWriter.write("Proxy-agent: UndefinedProxy1.0\r\n");
                        outputStreamWriter.write("\r\n");
                        outputStreamWriter.flush();

                        Thread remoteToClient = new Thread() {
                            @Override
                            public void run() {
                                forwardData(forwardSocket, clientSocket);
                            }
                        };
                        remoteToClient.start();
                        try {
                            if (previousWasR) {
                                int read = clientSocket.getInputStream().read();
                                if (read != -1) {
                                    if (read != '\n') {
                                        forwardSocket.getOutputStream().write(read);
                                    }
                                    forwardData(clientSocket, forwardSocket);
                                } else {
                                    if (!forwardSocket.isOutputShutdown()) {
                                        forwardSocket.shutdownOutput();
                                    }
                                    if (!clientSocket.isInputShutdown()) {
                                        clientSocket.shutdownInput();
                                    }
                                }
                            } else {
                                forwardData(clientSocket, forwardSocket);
                            }
                        } finally {
                            try {
                                remoteToClient.join();
                            } catch (InterruptedException e) {
                                e.printStackTrace();  // TODO: implement catch
                            }
                        }
                    } finally {
                        forwardSocket.close();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();  // TODO: implement catch
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();  // TODO: implement catch
                }
            }
        }

        private static void forwardData(Socket inputSocket, Socket outputSocket) {
            try {
                InputStream inputStream = inputSocket.getInputStream();
                try {
                    OutputStream outputStream = outputSocket.getOutputStream();
                    try {
                        byte[] buffer = new byte[4096];
                        int read;
                        do {
                            read = inputStream.read(buffer);
                            if (read > 0) {
                                outputStream.write(buffer, 0, read);
                                if (inputStream.available() < 1) {
                                    outputStream.flush();
                                }
                            }
                        } while (read >= 0);
                    } finally {
                        if (!outputSocket.isOutputShutdown()) {
                            outputSocket.shutdownOutput();
                        }
                    }
                } finally {
                    if (!inputSocket.isInputShutdown()) {
                        inputSocket.shutdownInput();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();  // TODO: implement catch
            }
        }

        private String readLine(Socket socket) throws IOException {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            int next;
            readerLoop:
            while ((next = socket.getInputStream().read()) != -1) {
                if (previousWasR && next == '\n') {
                    previousWasR = false;
                    continue;
                }
                previousWasR = false;
                switch (next) {
                    case '\r':
                        previousWasR = true;
                        break readerLoop;
                    case '\n':
                        break readerLoop;
                    default:
                        byteArrayOutputStream.write(next);
                        break;
                }
            }
            return byteArrayOutputStream.toString("ISO-8859-1");
        }
    }
}