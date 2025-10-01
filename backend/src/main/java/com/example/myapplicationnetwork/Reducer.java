package com.example.myapplicationnetwork;

import java.io.*;
import java.net.*;
import java.util.*;


public class Reducer {

    private static final int PORT = 9999;


    private static class TaskDataSearch {
        int expectedWorkers;
        int receivedWorkers;
        List<Store> partials;

        TaskDataSearch(int expected) {
            this.expectedWorkers = expected;
            this.receivedWorkers = 0;
            this.partials = new ArrayList<>();
        }
    }


    private static class TaskDataReport {
        int expectedWorkers;
        int receivedWorkers;
        List<Map<String,Integer>> partialMaps;

        TaskDataReport(int expected) {
            this.expectedWorkers = expected;
            this.receivedWorkers = 0;
            this.partialMaps = new ArrayList<>();
        }
    }


    private static final Map<Integer, TaskDataSearch> searchTasks =
            Collections.synchronizedMap(new HashMap<>());
    private static final Map<Integer, TaskDataReport> reportTasks =
            Collections.synchronizedMap(new HashMap<>());

    public static void main(String[] args) {
        System.out.println("[Reducer] Listening on port " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[Reducer] Accepted connection from "
                        + clientSocket.getRemoteSocketAddress());
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("[Reducer][ERROR] Could not start server: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private static void handleClient(Socket socket) {
        try {

            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            System.out.println("[Reducer] Created ObjectOutputStream to "
                    + socket.getRemoteSocketAddress());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            System.out.println("[Reducer] Created ObjectInputStream from "
                    + socket.getRemoteSocketAddress());

            Object headerObj = in.readObject();
            if (!(headerObj instanceof String)) {
                System.err.println("[Reducer] Invalid header from "
                        + socket.getRemoteSocketAddress());
                out.writeObject("ERROR");
                out.flush();
                closeEverything(out, in, socket);
                return;
            }
            String header = (String) headerObj;
            System.out.println("[Reducer] Received header: \"" + header
                    + "\" from " + socket.getRemoteSocketAddress());

            switch (header) {

                case "MASTER" -> {
                    int taskId = in.readInt();
                    int expectedWorkers = in.readInt();
                    System.out.println("[Reducer] New MASTER job. taskId="
                            + taskId + ", expectingWorkers="
                            + expectedWorkers);


                    synchronized (searchTasks) {
                        searchTasks.put(taskId, new TaskDataSearch(expectedWorkers));
                    }

                    out.writeObject("ACK");
                    out.flush();
                    System.out.println("[Reducer] Sent ACK for MASTER taskId=" + taskId);

                    closeEverything(out, in, socket);
                }


                case "WORKER" -> {

                    int taskId = in.readInt();

                    int workerId = in.readInt();

                    Object rawList = in.readObject();
                    if (!(rawList instanceof List<?>)) {
                        System.err.println("[Reducer] Expected List<Store> but got "
                                + rawList.getClass().getSimpleName());
                        out.writeObject("ERROR");
                        out.flush();
                        closeEverything(out, in, socket);
                        return;
                    }
                    List<Store> partials = (List<Store>) rawList;
                    System.out.println("[Reducer] Received " + partials.size()
                            + " stores from Worker " + workerId
                            + " for taskId=" + taskId);


                    TaskDataSearch tdSearch;
                    synchronized (searchTasks) {
                        tdSearch = searchTasks.get(taskId);
                    }
                    if (tdSearch == null) {
                        System.err.println("[Reducer] No TaskDataSearch for taskId="
                                + taskId + " (possibly outdated)");
                        out.writeObject("ERROR");
                        out.flush();
                        closeEverything(out, in, socket);
                        return;
                    }
                    synchronized (tdSearch) {
                        tdSearch.partials.addAll(partials);
                        tdSearch.receivedWorkers++;
                        System.out.println("[Reducer] taskId=" + taskId
                                + " → receivedWorkers="
                                + tdSearch.receivedWorkers + "/"
                                + tdSearch.expectedWorkers);
                    }

                    out.writeObject("ACK");
                    out.flush();
                    System.out.println("[Reducer] Sent ACK to Worker "
                            + workerId + " for taskId=" + taskId);

                    closeEverything(out, in, socket);
                }


                case "GET_RESULT" -> {
                    int taskId = in.readInt();
                    System.out.println("[Reducer] GET_RESULT for taskId=" + taskId);

                    TaskDataSearch tdSearch;
                    synchronized (searchTasks) {
                        tdSearch = searchTasks.get(taskId);
                    }
                    List<Store> finalReduced;
                    if (tdSearch == null || tdSearch.partials.isEmpty()) {
                        finalReduced = new ArrayList<>();
                    } else {
                        finalReduced = reduceSearchTask(tdSearch.partials);
                    }


                    out.writeObject(finalReduced);
                    out.flush();
                    System.out.println("[Reducer] Sent reduced Search result (size="
                            + finalReduced.size() + ") for taskId="
                            + taskId);


                    synchronized (searchTasks) {
                        searchTasks.remove(taskId);
                    }
                    closeEverything(out, in, socket);
                }

                case "MASTER_REPORT" -> {
                    int taskId = in.readInt();
                    int expectedWorkers = in.readInt();
                    System.out.println("[Reducer] New MASTER_REPORT job. taskId="
                            + taskId + ", expectingWorkers="
                            + expectedWorkers);


                    synchronized (reportTasks) {
                        reportTasks.put(taskId, new TaskDataReport(expectedWorkers));
                    }

                    out.writeObject("ACK");
                    out.flush();
                    System.out.println("[Reducer] Sent ACK for MASTER_REPORT taskId="
                            + taskId);

                    closeEverything(out, in, socket);
                }


                case "WORKER_REPORT" -> {
                    int taskId = in.readInt();
                    int workerId = in.readInt();
                    Object rawMap = in.readObject();
                    if (!(rawMap instanceof Map<?,?>)) {
                        System.err.println("[Reducer] Expected Map<String,Integer> but got "
                                + rawMap.getClass().getSimpleName());
                        out.writeObject("ERROR");
                        out.flush();
                        closeEverything(out, in, socket);
                        return;
                    }
                    Map<String,Integer> partialMap = (Map<String,Integer>) rawMap;
                    System.out.println("[Reducer] Received " + partialMap.size()
                            + " entries from Worker_REPORT " + workerId
                            + " for taskId=" + taskId);


                    TaskDataReport tdReport;
                    synchronized (reportTasks) {
                        tdReport = reportTasks.get(taskId);
                    }
                    if (tdReport == null) {
                        System.err.println("[Reducer] No TaskDataReport for taskId="
                                + taskId + " (possibly outdated)");
                        out.writeObject("ERROR");
                        out.flush();
                        closeEverything(out, in, socket);
                        return;
                    }
                    synchronized (tdReport) {
                        tdReport.partialMaps.add(partialMap);
                        tdReport.receivedWorkers++;
                        System.out.println("[Reducer] taskId=" + taskId
                                + " [REPORT] → receivedWorkers="
                                + tdReport.receivedWorkers + "/"
                                + tdReport.expectedWorkers);
                    }


                    out.writeObject("ACK");
                    out.flush();
                    System.out.println("[Reducer] Sent ACK to Worker_REPORT "
                            + workerId + " for taskId=" + taskId);

                    closeEverything(out, in, socket);
                }


                case "GET_REPORT" -> {
                    int taskId = in.readInt();
                    System.out.println("[Reducer] GET_REPORT for taskId=" + taskId);

                    TaskDataReport tdReport;
                    synchronized (reportTasks) {
                        tdReport = reportTasks.get(taskId);
                    }
                    Map<String,Integer> finalMap;
                    if (tdReport == null || tdReport.partialMaps.isEmpty()) {
                        finalMap = new HashMap<>();
                    } else {
                        finalMap = reduceReportTask(tdReport.partialMaps);
                    }


                    out.writeObject(finalMap);
                    out.flush();
                    System.out.println("[Reducer] Sent reduced Report result (size="
                            + finalMap.size() + ") for taskId=" + taskId);


                    synchronized (reportTasks) {
                        reportTasks.remove(taskId);
                    }

                    closeEverything(out, in, socket);
                }

                default -> {
                    System.err.println("[Reducer] Unknown header: " + header);
                    out.writeObject("ERROR");
                    out.flush();
                    closeEverything(out, in, socket);
                }
            }

        } catch (SocketException se) {
            System.err.println("[Reducer][ERROR] Connection closed abruptly: "
                    + se.getMessage());
        } catch (Exception e) {
            System.err.println("[Reducer][ERROR] " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
            e.printStackTrace();
            try {
                socket.close();
            } catch (IOException ignore) {}
        }
    }


    private static List<Store> reduceSearchTask(List<Store> allValues) {
        Map<String, Store> uniqueMap = new LinkedHashMap<>();
        for (Store s : allValues) {
            uniqueMap.putIfAbsent(s.getStoreName(), s);
        }
        return new ArrayList<>(uniqueMap.values());
    }


    private static Map<String,Integer> reduceReportTask(List<Map<String,Integer>> partials) {
        Map<String,Integer> merged = new HashMap<>();
        for (Map<String,Integer> m : partials) {
            for (Map.Entry<String,Integer> e : m.entrySet()) {
                merged.merge(e.getKey(), e.getValue(), Integer::max);
            }
        }
        return merged;
    }

    private static void closeEverything(ObjectOutputStream out,
                                        ObjectInputStream in,
                                        Socket socket) {
        try { if (out != null) out.close(); } catch (IOException ignore) {}
        try { if (in != null) in.close(); } catch (IOException ignore) {}
        try { if (socket != null) socket.close(); } catch (IOException ignore) {}
    }
}
