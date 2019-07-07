package com.company;

//STEP 1. Import required packages
import java.sql.*;

public class Main {
    static Connection conn;
    public static void main(String[] argv) throws ClassNotFoundException, SQLException {

        Class.forName("org.postgresql.Driver");

        conn = DriverManager.getConnection("jdbc:postgresql://ec2-54-246-84-100.eu-west-1.compute.amazonaws.com:5432/df1akdqbsk9ssm","pzkrduwxzmqirf","29ac0888c17302618b3ffd26971c1022481cc5a2602cae11e0e7aaf68595a593");

        ResultSet rs=request("SELECT unit_id FROM computers WHERE ip = '" + "192.168.1.1" + "'");
        while (rs.next()) {
            String id = rs.getString("unit_id");
            System.out.println(id + "\n");
        }
    }
    public static ResultSet request(String query) throws SQLException {
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        return rs;
    }
}