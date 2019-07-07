package com.company;

import java.sql.*;

public class SQLCONN {
    static Connection conn;
    public SQLCONN(String url,String user, String pwd) throws SQLException {
        conn = DriverManager.getConnection(url,user,pwd);
    }
    public static ResultSet request(String query) throws SQLException {
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        return rs;
    }
    public static void requestSend(String query) throws SQLException {
        Statement stmt = conn.createStatement();
        stmt.execute(query);
    }
}
