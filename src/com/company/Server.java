package com.company;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Server extends Thread {
    Scanner datfile=new Scanner(new File("database.txt"));
    SQLCONN sqlconn=new SQLCONN(datfile.nextLine(),datfile.nextLine(),datfile.nextLine());
    public static void main(String[] args) throws SQLException, FileNotFoundException {
        (new Server()).run();
    }

    public Server() throws SQLException, FileNotFoundException {

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
                    (new Handler(sqlconn,socket,id,s)).start();
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
        String ipc="";
        HashMap<String,String> list=new HashMap<>();
        SQLCONN sqlconn;
        public static final Pattern CONNECT_PATTERN = Pattern.compile("CONNECT (.+):(.+) HTTP/(1\\.[01])", Pattern.CASE_INSENSITIVE);
        private final Socket clientSocket;
        private boolean previousWasR = false;

        public Handler(SQLCONN sqlconn, Socket clientSocket, String id, String ipc)  {
            this.sqlconn=sqlconn;
            this.unitid=id;
            this.ipc=ipc;
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {

            try {
                ResultSet rs = sqlconn.request("SELECT * FROM black_list WHERE id IN (SELECT address_id FROM jopa WHERE unit_id = '" + unitid + "')");
                while(rs.next()){
                    ResultSet ts=sqlconn.request("SELECT jopa.id, bl.address,unit_id, time_limited, tl_start, tl_end " +
                            "FROM jopa " +
                            "INNER JOIN black_list bl on jopa.address_id = bl.id " +
                            "WHERE bl.address = '"+rs.getString("address")+"' " +
                            "AND unit_id = "+unitid+"");
                    if(ts.getBoolean("time_limited")){
                        long beg=ts.getLong("tl_start");
                        long end=ts.getLong("tl_end");
                        
                        Calendar c = Calendar.getInstance();
                        long now = c.getTimeInMillis();
                        c.set(Calendar.HOUR_OF_DAY, 0);
                        c.set(Calendar.MINUTE, 0);
                        c.set(Calendar.SECOND, 0);
                        c.set(Calendar.MILLISECOND, 0);
                        long passed = now - c.getTimeInMillis();
                        long secondsPassed = passed / 1000;
                        if(secondsPassed>beg&&secondsPassed<end){
                            list.put(rs.getString("address"),rs.getString("reason"));
                        }
                    }else{
                        list.put(rs.getString("address"),rs.getString("reason"));
                    }
                    //System.out.println(rs.getString("address")+": "+rs.getString("reason"));
                }
            }catch (Exception e){
                System.out.println("Wrong unit id!");
            }



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

                        //System.out.println(forwardSocket);
                    } catch (IOException | NumberFormatException e) {
                        e.printStackTrace();  // TODO: implement catch
                        outputStreamWriter.write("HTTP/" + matcher.group(3) + " 502 Bad Gateway\r\n");
                        outputStreamWriter.write("Proxy-agent: UndefinedProxy1.0\r\n");
                        outputStreamWriter.write("\r\n");
                        outputStreamWriter.flush();
                        return;
                    }
                    if(!checkForWLAndLog(matcher.group(1),clientSocket)) {
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
                            //forwardSocket.close();

                        }
                    }else{
                        System.out.println("BLACKLISTED!");
                    }
                }else{
                    System.out.println("Not a CONNECT request");
                    if (true) {
                        String temp = "";
                        do {
                            request += temp + "\r\n";
                            temp = (readLine(clientSocket));
                        } while (!temp.equals(""));
                        request += "\r\n";

                        String add = "";
                        Scanner s = new Scanner(request);
                        while (s.hasNextLine()) {
                            String[] sp = s.nextLine().split(": ");
                            if (sp[0].equals("Host")) {
                                add = sp[1];
                                break;
                            }
                        }
                        add = add.replaceAll("http\\:\\/\\/", "");
                        if (add.length() >= 1 && add.charAt(add.length() - 1) == '/')
                            add = add.substring(0, add.length() - 1);
                        //System.out.println(add);
                        Socket forwardSocket = new Socket(add, 80);
                        if(!checkForWLAndLog(add,clientSocket)) {
                            System.out.println(request);
                            OutputStream fos = forwardSocket.getOutputStream();
                            fos.write(request.getBytes());
                            fos.flush();
                            //fos.close();
                            Thread remoteToClient = new Thread() {
                                @Override
                                public void run() {
                                    //forwardData(clientSocket, forwardSocket);
                                    forwardData(forwardSocket, clientSocket);
                                }
                            };
                            remoteToClient.start();
                        }else {
                            System.out.println("BLACKLISTED!");
                        }
                    }else{
                        //System.out.println("Not even a GET request");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();  // TODO: implement catch
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                try {
                    //clientSocket.close();
                } catch (Exception e) {
                    e.printStackTrace();  // TODO: implement catch
                }
            }
        }

        private boolean checkForWLAndLog(String gg,Socket forwardSocket) throws IOException, SQLException {
            if(containsKeyl(list,gg)){
                String s="<html><body><h1>This site is blocked</h1><br><h3>Reason: "+list.get(gg)+"</h3></body></html>";
                String response = "HTTP/1.1 200 OK\r\n" +
                        "Server: UndefinedServer\r\n" +
                        "Content-Type: text/html\r\n" +
                        "Content-Length: " + s.length() + "\r\n" +
                        "Connection: close\r\n\r\n";
                String result = response + s;
                OutputStream os=forwardSocket.getOutputStream();
                os.write(result.getBytes());
                os.flush();
                sqlconn.requestSend("INSERT INTO logs(computer_id, address, is_success) SELECT computers.id, '"+gg+"', "+"FALSE"+" FROM computers WHERE computers.ip = '"+ipc+"'");
                return true;
            }
            sqlconn.requestSend("INSERT INTO logs(computer_id, address, is_success) SELECT computers.id, '"+gg+"', "+"TRUE"+" FROM computers WHERE computers.ip = '"+ipc+"'");
            return false;
        }

        private boolean containsKeyl(HashMap<String, String> list, String gg) {
            for (String t:list.keySet()) {
                //System.out.println(t+" "+gg);
                if(t.equals(gg)){
                    //System.out.println("O MY GOWD!");
                    return true;
                }
            }
            return false;
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