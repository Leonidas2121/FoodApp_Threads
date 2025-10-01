package com.example.myapplicationnetwork;

import java.io.File;
import java.io.IOException;

public class AddWorker {
    public static void main(String[] args) {
        int id = Integer.parseInt(args[0]);
        int port = Integer.parseInt(args[1]);
        String masterIp = args[2];

        String javaHome = System.getProperty("java.home");
        String javaBin  = javaHome + File.separator + "bin" + File.separator + "java";
        String classpath = System.getProperty("java.class.path");



        String command = String.format(
                "cmd /c start cmd /k \"\"%s\" -Dfile.encoding=UTF-8 -cp \"%s\" com.example.myapplicationnetwork.Worker %d %d %s\"",
                javaBin, classpath, id, port, masterIp
        );

        try {
            new ProcessBuilder("cmd", "/c", command).start();
            System.out.println("[AddWorker] Started remote Worker with id=" + id);
        } catch (IOException e) {
            System.err.println("Failed to start Worker: " + e.getMessage());
        }
    }
}
