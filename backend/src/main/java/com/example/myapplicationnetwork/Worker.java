// Worker.java
package com.example.myapplicationnetwork;

import java.io.*;
import java.net.*;
import java.util.*;

public class Worker implements Runnable {

    private static final Map<String, Store> storeData = new HashMap<>();

    private final int id;
    private final int port;
    private final String masterIp;

    public Worker(int id, int port, String masterIp) {
        this.id = id;
        this.port = port;
        this.masterIp = masterIp;
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java Worker <id> <port> [masterIp]");
            return;
        }

        int id = Integer.parseInt(args[0]);
        int port = Integer.parseInt(args[1]);
        String masterIp = (args.length >= 3) ? args[2] : "localhost";

        Worker worker = new Worker(id, port, masterIp);
        new Thread(worker).start();
    }


    private static volatile boolean running = true;

    @Override
    public void run() {

        try (Socket socket = new Socket(masterIp, 4321);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            out.writeObject("REGISTER_WORKER:port=" + port);
            out.flush();

            Object response = in.readObject();
            System.out.println("[Worker " + id + "] Registration response: " + response);

        } catch (Exception e) {
            System.err.println("[Worker " + id + "] Failed to register with Master: " + e.getMessage());
        }


        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("[Worker " + id + "] Listening on port " + port);
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    new Thread(new WorkerHandler(socket, masterIp, this.id)).start();
                } catch (IOException e) {
                    if (running) {
                        System.err.println("[Worker " + id + "] Σφάλμα: " + e.getMessage());
                    }
                    break; // έξοδος από loop αν κλείσει το running
                }
            }
        } catch (IOException e) {
            System.err.println("[Worker " + id + "] Σφάλμα κατά το άνοιγμα της πόρτας: " + e.getMessage());
        } finally {
            System.out.println("[Worker " + id + "] Shutting down.");
        }
    }


    static class WorkerHandler extends Thread {
        private final Socket socket;
        private final int id;
        private final String masterIp;
        private static final double EARTH_RADIUS = 6371;

        public WorkerHandler(Socket socket, String masterIp, int id) {
            this.socket = socket;
            this.masterIp = masterIp;
            this.id = id;
        }

        public void run() {
            System.out.println("[WorkerHandler] Thread started for connection: " + socket);
            try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                System.out.println("[WorkerHandler][DEBUG] Streams ready. Reading input...");
                Object input = in.readObject();
                System.out.println("[WorkerHandler] Received input: " + input);


                if (input instanceof Store store) {

                    synchronized (storeData) {
                        storeData.put(store.getStoreName(), store);
                    }
                    System.out.println("[WorkerHandler] Store added: " + store.getStoreName());
                    out.writeObject("Κατάστημα " + store.getStoreName() + " αποθηκεύτηκε επιτυχώς.");
                    out.flush();
                    return;
                }

                else if (input instanceof String cmd) {
                    System.out.println("[WorkerHandler] Processing command: " + cmd);


                    if (cmd.startsWith("MAP:")) {

                        int semiIdx = cmd.indexOf(';');
                        int taskId;
                        try {
                            taskId = Integer.parseInt(cmd.substring(4, semiIdx).trim());
                        } catch (NumberFormatException e) {
                            out.writeObject("ERROR: Invalid taskId in MAP");
                            out.flush();
                            return;
                        }


                        int searchIdx = cmd.indexOf("SEARCH:");
                        if (searchIdx < 0) {
                            out.writeObject("ERROR: SEARCH: not found after MAP:");
                            out.flush();
                            return;
                        }
                        String searchFilters = cmd.substring(searchIdx + "SEARCH:".length()).trim();
                        System.out.println("[WorkerHandler] taskId=" + taskId + " — filters=" + searchFilters);


                        Map<String, String> filters = parseFilters(searchFilters);
                        double clientLat = Double.parseDouble(filters.getOrDefault("latitude", "0"));
                        double clientLon = Double.parseDouble(filters.getOrDefault("longitude", "0"));


                        List<Store> matchedStores = new ArrayList<>();
                        synchronized (storeData) {
                            for (Store s : storeData.values()) {
                                if (matchesFilters(s, filters, clientLat, clientLon)) {
                                    matchedStores.add(s);
                                }
                            }
                        }
                        System.out.println("[WorkerHandler] Βρέθηκαν "
                                + matchedStores.size() + " stores για task " + taskId);


                        sendToReducer(taskId, matchedStores);
                        System.out.println("[WorkerHandler][DEBUG] Returned from sendToReducer for taskId=" + taskId);


                        String ack = "ACK_MAP_RECEIVED";
                        System.out.println("[WorkerHandler] Sending ACK to Master: " + ack);
                        out.writeObject(ack);
                        out.flush();
                        System.out.println("[WorkerHandler][STEP1] Flushed ACK_MAP_RECEIVED. Returning.");
                        return;
                    }


                    else if (cmd.startsWith("BUY:")) {
                        String[] parts = cmd.substring(4).split(";");
                        String storeName = null, product = null;
                        int qty = 0;
                        for (String part : parts) {
                            String[] kv = part.split("=");
                            if (kv[0].equals("storeName")) storeName = kv[1];
                            else if (kv[0].equals("product")) product = kv[1];
                            else if (kv[0].equals("quantity")) qty = Integer.parseInt(kv[1]);
                        }

                        boolean success = false;
                        synchronized (storeData) {
                            Store store = storeData.get(storeName);
                            if (store != null) {
                                success = store.registerSale(product, qty);
                            }
                        }
                        out.writeObject(success ? "Purchase successful" : "Purchase failed");
                        out.flush();
                        return;
                    }


                    else if (cmd.startsWith("UPDATE_STOCK:")) {
                        String[] parts = cmd.substring(13).split(";");
                        String storeName = null, product = null;
                        int amount = 0;
                        for (String part : parts) {
                            String[] kv = part.split("=");
                            if (kv[0].equals("store")) storeName = kv[1];
                            else if (kv[0].equals("product")) product = kv[1];
                            else if (kv[0].equals("amount")) amount = Integer.parseInt(kv[1]);
                        }

                        boolean updated = false;
                        synchronized (storeData) {
                            Store store = storeData.get(storeName);
                            if (store != null) {
                                updated = store.updateStock(product, amount);
                            }
                        }
                        out.writeObject(updated ? "Stock ενημερώθηκε." : "Αποτυχία ενημέρωσης.");
                        out.flush();
                        return;
                    }


                    else if (cmd.startsWith("ADD_PRODUCT:")) {
                        String[] parts = cmd.substring("ADD_PRODUCT:".length()).split(";");
                        String storeName = null, name = null, type = null;
                        int amount = 0;
                        double price = 0;
                        for (String part : parts) {
                            String[] kv = part.split("=");
                            switch (kv[0]) {
                                case "store"  -> storeName = kv[1];
                                case "name"   -> name = kv[1];
                                case "type"   -> type = kv[1];
                                case "amount" -> amount = Integer.parseInt(kv[1]);
                                case "price"  -> price = Double.parseDouble(kv[1]);
                            }
                        }

                        boolean added = false;
                        synchronized (storeData) {
                            Store store = storeData.get(storeName);
                            if (store != null) {

                                added = store.addProduct(name, type, amount, price);


                                if (added) {
                                    double sum = 0;
                                    for (Product p : store.getProducts()) {
                                        sum += p.getPrice();
                                    }
                                    double avg = sum / store.getProducts().size();
                                    if (avg <= 5) {
                                        store.setPriceCategory("$");
                                    } else if (avg <= 15) {
                                        store.setPriceCategory("$$");
                                    } else {
                                        store.setPriceCategory("$$$");
                                    }
                                }
                            }
                        }

                        out.writeObject(added ? "Προϊόν προστέθηκε." : "Αποτυχία προσθήκης.");
                        out.flush();
                        return;
                    }



                    else if (cmd.startsWith("RATE:")) {
                        String[] parts = cmd.substring(5).split(";");
                        String storeName = null;
                        int rating = 0;
                        for (String part : parts) {
                            String[] kv = part.split("=");
                            if (kv[0].equals("storeName")) storeName = kv[1];
                            else if (kv[0].equals("rating")) rating = Integer.parseInt(kv[1]);
                        }
                        boolean success = false;
                        synchronized (storeData) {
                            Store store = storeData.get(storeName);
                            if (store != null) {
                                store.addRating(rating);
                                success = true;
                            }
                        }
                        out.writeObject(success ? "Βαθμολογία καταχωρήθηκε." : "Αποτυχία καταχώρησης βαθμολογίας.");
                        out.flush();
                        return;
                    }


                    else if (cmd.startsWith("SYNC_UPDATE:")) {
                        String[] parts = cmd.substring("SYNC_UPDATE:".length()).split(";");
                        String storeName = null, product = null;
                        int sales = 0;
                        for (String part : parts) {
                            String[] kv = part.split("=");
                            if (kv[0].equals("storeName")) storeName = kv[1];
                            else if (kv[0].equals("product")) product = kv[1];
                            else if (kv[0].equals("sales")) sales = Integer.parseInt(kv[1]);
                            System.out.println("[WorkerHandler] storeName=" + storeName + ", product=" + product + ", sales=" + sales);
                        }
                        synchronized (storeData) {
                            Store store = storeData.get(storeName);
                            if (store != null) {
                                store.syncSale(product, sales);
                            }
                        }
                        out.writeObject("Replica synchronized.");
                        out.flush();
                        return;
                    }


                    else if (cmd.startsWith("REMOVE_PRODUCT:")) {
                        String[] parts = cmd.substring(15).split(";");
                        String storeName = null, productName = null;
                        for (String part : parts) {
                            String[] kv = part.split("=");
                            if (kv[0].equals("store")) storeName = kv[1];
                            else if (kv[0].equals("product")) productName = kv[1];
                        }
                        boolean removed = false;
                        synchronized (storeData) {
                            Store store = storeData.get(storeName);
                            if (store != null) {
                                removed = store.removeProduct(productName);
                            }
                        }
                        out.writeObject(removed ? "Προϊόν αφαιρέθηκε." : "Αποτυχία αφαίρεσης.");
                        out.flush();
                        return;
                    }


                    else if (cmd.startsWith("REPORT:productName=")) {

                        String prefix = "REPORT:productName=";
                        
                        int firstSemicolon = cmd.indexOf(';', prefix.length());
                        if (firstSemicolon < 0) {
                            out.writeObject("ERROR: Malformed REPORT:productName=");
                            out.flush();
                            return;
                        }


                        String taskIdStr = cmd.substring(prefix.length(), firstSemicolon).trim();
                        int taskId;
                        try {
                            taskId = Integer.parseInt(taskIdStr);
                        } catch (NumberFormatException e) {
                            out.writeObject("ERROR: Invalid taskId in REPORT:productName=");
                            out.flush();
                            return;
                        }


                        String remainder = cmd.substring(firstSemicolon + 1);


                        int secondSemicolon = remainder.indexOf(';');
                        String productNameRaw;
                        if (secondSemicolon >= 0) {

                            productNameRaw = remainder.substring(0, secondSemicolon).trim();

                        } else {

                            productNameRaw = remainder.trim();
                        }

                        if (productNameRaw.isEmpty()) {
                            out.writeObject("ERROR: Missing productName in REPORT:productName=");
                            out.flush();
                            return;
                        }


                        String productName = productNameRaw.toLowerCase();


                        Map<String, Integer> reportByProduct = new HashMap<>();
                        synchronized (storeData) {
                            for (Store s : storeData.values()) {
                                int sales = s.getSalesByProductName(productName);
                                if (sales > 0) {
                                    reportByProduct.put(s.getStoreName(), sales);
                                }
                            }
                        }


                        sendReportToReducer(taskId, reportByProduct);


                        out.writeObject("ACK_REPORT_RECEIVED");
                        out.flush();
                        return;
                        }




                    else if (cmd.startsWith("GET_STORE_STATUS:")) {
                        String storeName = cmd.substring("GET_STORE_STATUS:storeName=".length()).trim();
                        synchronized (storeData) {
                            Store store = storeData.get(storeName);
                            out.writeObject(store != null ? store : "Store not found");
                        }
                        out.flush();
                        return;
                    }


                    else if (cmd.startsWith("REPORT_BY_CATEGORY:")) {
                        // 1) Παίρνουμε taskId
                        int semicolonIdx = cmd.indexOf(';', "REPORT_BY_CATEGORY:".length());
                        if (semicolonIdx < 0) {
                            out.writeObject("ERROR: Malformed REPORT_BY_CATEGORY");
                            out.flush();
                            return;
                        }
                        String taskIdStr = cmd.substring("REPORT_BY_CATEGORY:".length(), semicolonIdx).trim();
                        int taskId;
                        try {
                            taskId = Integer.parseInt(taskIdStr);
                        } catch (NumberFormatException e) {
                            out.writeObject("ERROR: Invalid taskId in REPORT_BY_CATEGORY");
                            out.flush();
                            return;
                        }

                        String rest = cmd.substring(semicolonIdx + 1).trim();
                        if (!rest.startsWith("FoodCategory=")) {
                            out.writeObject("ERROR: Missing FoodCategory=");
                            out.flush();
                            return;
                        }
                        String category = rest.substring("FoodCategory=".length()).trim();


                        Map<String, Integer> reportByCategory = new HashMap<>();
                        synchronized (storeData) {
                            for (Store s : storeData.values()) {
                                if (s.getFoodCategory().equalsIgnoreCase(category)) {
                                    reportByCategory.put(s.getStoreName(), s.getTotalSales());
                                }
                            }
                        }

                        sendReportToReducer(taskId, reportByCategory);

                        // ACK στον Master
                        out.writeObject("ACK_REPORT_RECEIVED");
                        out.flush();
                        return;
                    }

                    else if (cmd.startsWith("REPORT_BY_TYPE:")) {
                        int semicolonIdx = cmd.indexOf(';', "REPORT_BY_TYPE:".length());
                        if (semicolonIdx < 0) {
                            out.writeObject("ERROR: Malformed REPORT_BY_TYPE");
                            out.flush();
                            return;
                        }
                        String taskIdStr = cmd.substring("REPORT_BY_TYPE:".length(), semicolonIdx).trim();
                        int taskId;
                        try {
                            taskId = Integer.parseInt(taskIdStr);
                        } catch (NumberFormatException e) {
                            out.writeObject("ERROR: Invalid taskId in REPORT_BY_TYPE");
                            out.flush();
                            return;
                        }
                        String rest = cmd.substring(semicolonIdx + 1).trim();
                        if (!rest.startsWith("ProductType=")) {
                            out.writeObject("ERROR: Missing ProductType=");
                            out.flush();
                            return;
                        }
                        String productType = rest.substring("ProductType=".length()).trim();


                        Map<String, Integer> reportByType = new HashMap<>();
                        synchronized (storeData) {
                            for (Store s : storeData.values()) {
                                int sales = s.getSalesByProductType(productType);
                                if (sales > 0) {
                                    reportByType.put(s.getStoreName(), sales);
                                }
                            }
                        }

                        sendReportToReducer(taskId, reportByType);


                        out.writeObject("ACK_REPORT_RECEIVED");
                        out.flush();
                        return;
                    }
                    else if (cmd.startsWith("GET_TOTAL_SALES:")) {
                        String[] parts = cmd.substring("GET_TOTAL_SALES:".length()).split(";");
                        String storeName = null, product = null;
                        for (String part : parts) {
                            String[] kv = part.split("=");
                            if (kv[0].equals("storeName")) storeName = kv[1];
                            else if (kv[0].equals("product"))   product   = kv[1];
                        }
                        int totalSales = 0;
                        synchronized (storeData) {
                            Store store = storeData.get(storeName);
                            if (store != null) {

                                totalSales = store.getSalesByProductName(product);
                            }
                        }
                        System.out.println("[WorkerHandler] Sales= "+totalSales);
                        Map<String, Integer> result = new HashMap<>();
                        result.put("total", totalSales);
                        out.writeObject(result);
                        out.flush();
                        return;
                    }



                    else if (cmd.equals("TERMINATE")) {
                        System.out.println("[WorkerHandler] Τερματισμός worker...");
                        out.writeObject("BYE");
                        out.flush();
                        Worker.running = false;
                        socket.close();
                        return;
                    }

                    else {
                        out.writeObject("UNKNOWN_COMMAND");
                        out.flush();
                        return;
                    }
                }


                out.writeObject("INVALID_INPUT");
                out.flush();

            } catch (SocketException e) {
                System.out.println("[WorkerHandler] Σύνδεση τερματίστηκε: " + e.getMessage());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        private void sendToReducer(int taskId, List<Store> stores) {
            System.out.println("[WorkerHandler][DEBUG] Entering sendToReducer for taskId=" + taskId);
            Socket reducerSocket = null;
            try {
                System.out.println("[WorkerHandler][DEBUG] Attempting new Socket(\"localhost\", 9999) …");
                reducerSocket = new Socket("localhost", 9999);
                System.out.println("[WorkerHandler][DEBUG] Connected to Reducer: " + reducerSocket.getRemoteSocketAddress());
            } catch (Exception e) {
                System.err.println("[WorkerHandler][ERROR] Could not open socket to Reducer: "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
                e.printStackTrace();
                return;
            }

            try {

                ObjectOutputStream outToReducer = new ObjectOutputStream(reducerSocket.getOutputStream());
                System.out.println("[WorkerHandler][DEBUG] About to write header \"WORKER\" to Reducer");
                outToReducer.writeObject("WORKER");
                outToReducer.flush();
                System.out.println("[WorkerHandler][DEBUG] Wrote header \"WORKER\" to Reducer");


                outToReducer.writeInt(taskId);
                outToReducer.flush();
                System.out.println("[WorkerHandler][DEBUG] Wrote taskId=" + taskId + " to Reducer");


                outToReducer.writeInt(this.id);
                outToReducer.flush();
                System.out.println("[WorkerHandler][DEBUG] Wrote workerId=" + this.id + " to Reducer");

                outToReducer.writeObject(stores);
                outToReducer.flush();
                System.out.println("[WorkerHandler][DEBUG] Sent stores list (size="
                        + stores.size() + ") to Reducer");

                ObjectInputStream inFromReducer = new ObjectInputStream(reducerSocket.getInputStream());
                System.out.println("[WorkerHandler][DEBUG] Waiting for ack from Reducer…");
                Object ack = inFromReducer.readObject();
                System.out.println("[WorkerHandler][DEBUG] Received ack from Reducer: " + ack);
            } catch (Exception e) {
                System.err.println("[WorkerHandler][ERROR] During communication with Reducer: "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
                e.printStackTrace();
            } finally {
                try {
                    if (reducerSocket != null) reducerSocket.close();
                } catch (IOException ignored) {}
                System.out.println("[WorkerHandler][DEBUG] Exiting sendToReducer for taskId=" + taskId);
            }
        }


        private void sendReportToReducer(int taskId, Map<String,Integer> reportMap) {
            try (Socket reducerSocket = new Socket("localhost", 9999)) {
                ObjectOutputStream outToReducer = new ObjectOutputStream(reducerSocket.getOutputStream());
                ObjectInputStream  inFromReducer = new ObjectInputStream(reducerSocket.getInputStream());

                // 1) Header
                outToReducer.writeObject("WORKER_REPORT");
                outToReducer.flush();
                // 2) taskId
                outToReducer.writeInt(taskId);
                outToReducer.flush();
                // 3) workerId
                outToReducer.writeInt(this.id);
                outToReducer.flush();
                // 4) To Map<String,Integer> με το partial report
                outToReducer.writeObject(reportMap);
                outToReducer.flush();

                // Περιμένουμε ACK
                Object ack = inFromReducer.readObject();
                System.out.println("[WorkerHandler-" + this.id + "] Received ack from Reducer: " + ack);
            } catch (Exception e) {
                System.err.println("[WorkerHandler-" + this.id + "] Error sending report to Reducer: " + e.getMessage());
                e.printStackTrace();
            }
        }


        private Map<String, String> parseFilters(String input) {
            Map<String, String> map = new HashMap<>();
            for (String part : input.split(";")) {
                String[] kv = part.split("=");
                if (kv.length == 2)
                    map.put(kv[0].trim(), kv[1].trim());
            }
            return map;
        }


        private double extractDouble(String cmd, String key) {
            for (String part : cmd.split(";")) {
                String[] kv = part.split("=");
                if (kv.length == 2 && kv[0].trim().equals(key)) {
                    try {
                        return Double.parseDouble(kv[1].trim());
                    } catch (NumberFormatException e) {
                        return 0.0;
                    }
                }
            }
            return 0.0;
        }


        private boolean matchesFilters(Store store, Map<String, String> filters, double clientLat, double clientLon) {

            String rawCat = filters.getOrDefault("category", filters.get("FoodCategory"));
            if (rawCat != null && !rawCat.isEmpty()) {

                String[] cats = rawCat.split(",");
                boolean anyMatch = false;
                String storeCat = store.getFoodCategory().trim().toLowerCase();
                for (String c : cats) {
                    if (storeCat.equals(c.trim().toLowerCase())) {
                        anyMatch = true;
                        break;
                    }
                }
                if (!anyMatch) {
                    System.out.println("[WorkerHandler] ΑΠΟΡΡΙΠΤΕΤΑΙ λόγω category: " + store.getFoodCategory());
                    return false;
                }
            }

            // --- STARS ---
            if (filters.containsKey("stars")) {
                int min = Integer.parseInt(filters.get("stars"));
                if (store.getStars() < min) {
                    System.out.println("[WorkerHandler] ΑΠΟΡΡΙΠΤΕΤΑΙ λόγω stars: " + store.getStars());
                    return false;
                }
            }

            // --- PRICE ---
            if (filters.containsKey("price")) {
                String pr = filters.get("price");
                if (!pr.equals(store.getPriceCategory())) {
                    System.out.println("[WorkerHandler] ΑΠΟΡΡΙΠΤΕΤΑΙ λόγω τιμής: " + store.getPriceCategory());
                    return false;
                }
            }

            // --- ΑΠΟΣΤΑΣΗ ---
            if (clientLat != 0.0 && clientLon != 0.0) {
                double dist = calculateDistance(clientLat, clientLon, store.getLatitude(), store.getLongitude());
                if (dist > 5.0) {
                    System.out.println("[WorkerHandler] ΑΠΟΡΡΙΠΤΕΤΑΙ λόγω απόστασης: " + dist + " km");
                    return false;
                }
            }

            // Αν περάσει όλα τα checks
            return true;
        }


        private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
            lat1 = Math.toRadians(lat1);
            lon1 = Math.toRadians(lon1);
            lat2 = Math.toRadians(lat2);
            lon2 = Math.toRadians(lon2);
            double dlon = lon2 - lon1;
            double dlat = lat2 - lat1;
            double a = Math.pow(Math.sin(dlat / 2), 2)
                    + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dlon / 2), 2);
            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
            return EARTH_RADIUS * c;
        }
    }
}
