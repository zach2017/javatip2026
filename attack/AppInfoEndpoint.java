package com.example.demo.admin;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.stereotype.Component;
import java.net.ServerSocket;
import java.io.*;

@Component
@Endpoint(id = "appinfo")
public class AppInfoEndpoint {

    @ReadOperation
    public String info() {
        return "build-info";
    }

    @WriteOperation
    public void reload(String profile) throws IOException {
        // State-changing actuator op
        FileWriter fw = new FileWriter("/etc/app/profile.txt");
        fw.write(profile);
        fw.close();
    }

    @DeleteOperation
    public void wipe() {
        // Destructive actuator op
    }

    public void startTcpListener() throws IOException {
        // Raw inbound socket listener
        ServerSocket server = new ServerSocket(9999);
        server.accept();
    }
}
