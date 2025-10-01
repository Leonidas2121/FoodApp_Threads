package com.example.myapplicationnetwork;
import java.io.*;
import java.net.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Master {
    private static final int PORT = 4321;
    private static final Map<Integer, String> activeWorkers =
            Collections.synchronizedMap(new HashMap<>());
    private static int nextMapId = 0;
    private static final Map<String, List<Integer>> storeToWorkers = new HashMap<>();
    private static final Map<Integer, List<String>> workerToStore = new HashMap<>();

    private static final Map<String, Store> allStores = new HashMap<>();



    private static final int BASE_WORKER_PORT = 5000;

    public static void main(String[] args) throws IOException {
        // default τιμές
        String host = "localhost";
        int numWorkers = 4;

        // Αν έχουμε τουλάχιστον ένα όρισμα, το θεωρούμε host
        if (args.length >= 1) {
            host = args[0];
        }

        // Αν έχουμε δεύτερο όρισμα, το προσπαθούμε να το κάνουμε int για workers
        if (args.length >= 2) {
            try {
                numWorkers = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("⚠ Μη έγκυρος αριθμός workers (“" + args[1] + "”), χρησιμοποιείται default = 4");
            }
        }

        System.out.println("[Master] Starting on IP " + host + " with " + numWorkers + " workers.");


        ServerSocket serverSocket = new ServerSocket(PORT, 50, InetAddress.getByName(host));
        System.out.println("[Master] Listening on " + host + ":" + PORT);

        startWorkers(numWorkers,host);

        while (true) {
            Socket clientSocket = serverSocket.accept();
            new Thread(new ClientHandler(clientSocket,host)).start();
        }
    }



    private static void startWorkers(int numWorkers,String host) {
        String javaHome = System.getProperty("java.home");
        String javaBin = "\"" + javaHome + File.separator + "bin" + File.separator + "java" + "\"";


        String classpath = String.join(";", Arrays.asList(
                "C:\\Users\\user\\.gradle\\caches\\modules-2\\files-2.1\\com.fasterxml.jackson.core\\jackson-annotations\\2.15.0\\89b0fd554928425a776a6e97ed010034312af21d\\jackson-annotations-2.15.0.jar",
                "C:\\Users\\user\\.gradle\\caches\\modules-2\\files-2.1\\com.fasterxml.jackson.core\\jackson-core\\2.15.0\\12f334a1dc9c6d2854c43ae314024dde8b3ad572\\jackson-core-2.15.0.jar",
                "C:\\Users\\user\\.gradle\\caches\\modules-2\\files-2.1\\com.fasterxml.jackson.core\\jackson-databind\\2.15.0\\d41caa3a4e9f85382702a059a65c512f85ac230\\jackson-databind-2.15.0.jar",
                "C:\\Users\\user\\.gradle\\caches\\modules-2\\files-2.1\\ch.randelshofer\\fastdoubleparser\\0.8.0\\85c25540369921659556ead85e02c99ef0d24280\\fastdoubleparser-0.8.0.jar",
                "C:\\Users\\user\\Desktop\\p3220013_p3220142_p3220166\\MyApplicationNetwork\\backend\\build\\classes\\java\\main"
        ));

        for (int i = 0; i < numWorkers; i++) {
            int port = 5000 + i;
            try {
                String command = String.format(
                        "cmd /c start \"Worker %d\" cmd /k \"%s -cp \\\"%s\\\" " +
                                "com.example.myapplicationnetwork.Worker %d %d %s\"",
                        i,            // %d → worker id
                        javaBin,      // %s → java binary
                        classpath,    // %s → classpath
                        i,            // %d → worker id (arg[0])
                        port,         // %d → worker port (arg[1])
                        host          // %s → masterIp  (arg[2])
                );



                ProcessBuilder pb = new ProcessBuilder("cmd", "/c", command);
                pb.start();

                System.out.println("[Master] Started Worker " + i + " on port " + port);
            } catch (IOException e) {
                System.err.println("[Master] Failed to start Worker " + i + ": " + e.getMessage());
            }
        }
    }



    static class WorkersCoordinator {
        int remaining;

        public WorkersCoordinator(int initial) {
            this.remaining = initial;
        }

        public synchronized void workerDone() {
            remaining--;
            if (remaining <= 0) {
                notifyAll();
            }
        }

        public synchronized void waitForAll() throws InterruptedException {
            while (remaining > 0) {
                wait();
            }
        }
    }

    static class WorkerSenderThread extends Thread {
        private final String host;
        private final int port;
        private final String command;
        private final WorkersCoordinator coordinator;

        public WorkerSenderThread(String host, int port, String command, WorkersCoordinator coord) {
            this.host = host;
            this.port = port;
            this.command = command;
            this.coordinator = coord;
        }

        @Override
        public void run() {
            try (
                    Socket socketToWorker = new Socket(host, port);
                    ObjectOutputStream outToWorker = new ObjectOutputStream(socketToWorker.getOutputStream());
                    ObjectInputStream  inFromWorker = new ObjectInputStream(socketToWorker.getInputStream());
            ) {
                System.out.println("[Master] (Thread) Sending to Worker " + host + ":" + port + " → " + command);
                outToWorker.writeObject(command);
                outToWorker.flush();

                Object ackRaw = inFromWorker.readObject();
                if (ackRaw instanceof String) {
                    System.out.println("[Master] (Thread) ACK από Worker " + host + ":" + port + " → " + ackRaw);
                } else {
                    System.err.println("[Master] (Thread) Μη αναμενόμενος τύπος ACK από " + host + ":" + port);
                }
            } catch (Exception e) {
                System.err.println("[Master][ERROR] Worker on port " + port + " unreachable. Killing...");
                int workerId = port - BASE_WORKER_PORT;
                killWorker(workerId);
            } finally {
                coordinator.workerDone();
            }
        }
    }



    static class StoreSenderThread extends Thread {
        private final String host;
        private final int port;
        private final Store storeObject;
        private final WorkersCoordinator coordinator;

        private final List<Boolean> results;

        public StoreSenderThread(String host, int port, Store storeObject,
                                 WorkersCoordinator coord, List<Boolean> results) {
            this.host = host;
            this.port = port;
            this.storeObject = storeObject;
            this.coordinator = coord;
            this.results = results;
        }

        @Override
        public void run() {
            boolean ok = false;
            try (
                    Socket socketToWorker = new Socket(host, port);
                    ObjectOutputStream out = new ObjectOutputStream(socketToWorker.getOutputStream());
                    ObjectInputStream  in  = new ObjectInputStream(socketToWorker.getInputStream());
            ) {

                out.writeObject(storeObject);
                out.flush();


                Object resp = in.readObject();
                if (resp instanceof String) {
                    String str = ((String) resp).toLowerCase();

                    if (str.contains("επιτυχώς")) {
                        ok = true;
                    }
                }
            } catch (Exception e) {

                System.err.println("[Master][ERROR] Worker on port " + port + " unreachable. Killing...");
                int workerId = port - BASE_WORKER_PORT;
                killWorker(workerId);
                ok = false;
            } finally {

                synchronized (results) {
                    results.add(ok);
                }
                coordinator.workerDone();
            }
        }
    }








    static class ClientHandler extends Thread {
        private final Socket clientSocket;
        private final String Host;

        public ClientHandler(Socket socket,String Host) {
            this.clientSocket = socket;
            this.Host = Host;
        }

        public void run() {
            try {
                ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                out.flush();
                ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());

                Object input = in.readObject();

                if (input instanceof String cmd) {
                    System.out.println("[Master] Received command: " + cmd);

                    if (cmd.startsWith("REGISTER_WORKER:")) {
                        int port = Integer.parseInt(extractValue(cmd, "port", null));
                        String ip = clientSocket.getInetAddress().getHostAddress();
                        synchronized(activeWorkers) {
                            if (!activeWorkers.containsKey(port)) {
                                activeWorkers.put(port, ip);
                                System.out.println(
                                        "[Master] Registered worker " + ip + ":" + port);
                            }
                        }
                        out.writeObject("OK: Worker registered on " + ip + ":" + port);
                    }

                    else if (cmd.startsWith("SEARCH:")) {
                        handleSearch(cmd, out);
                    }

                    else if (cmd.startsWith("REPORT:productName=")) {
                        handleReport(cmd, out);
                    }

                    else if (cmd.startsWith("REPORT:") || cmd.startsWith("REPORT_BY_")) {
                        handleReport(cmd, out);
                    }

                    else if (cmd.startsWith("BUY:") || cmd.startsWith("UPDATE_STOCK:") ||
                            cmd.startsWith("ADD_PRODUCT:") || cmd.startsWith("REMOVE_PRODUCT:") ||
                            cmd.startsWith("RATE:")) {

                        handleCommandWithFailover(cmd, out);
                    }

                    else if (cmd.startsWith("GET_STORE_STATUS:")) {
                        handleStoreStatus(cmd, out);
                    }else if (cmd.startsWith("KILL_WORKER:")) {
                            int id = Integer.parseInt(extractValue(cmd, "workerId", null));
                            String result = killWorker(id);
                            out.writeObject(result);
                        }




                    else {
                        try {
                            Store store = new ObjectMapper().readValue(cmd, Store.class);
                            calculatePriceCategory(store);
                            assignStoreToWorkers(store.getStoreName());
                            boolean ok = sendStoreToAllReplicas(store);
                            if (ok){
                                allStores.put(store.getStoreName(), store);
                            }
                            out.writeObject(ok ? "Κατάστημα προστέθηκε." : "Αποτυχία αποστολής καταστήματος στον Worker.");
                        } catch (Exception e) {
                            out.writeObject("Σφάλμα κατά την ανάγνωση JSON: " + e.getMessage());
                        }
                    }
                } else {
                    out.writeObject("Άγνωστη εντολή ή τύπος δεδομένων.");
                }

                out.flush();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }








        private void handleSearch(String cmd, ObjectOutputStream outToClient)
                throws IOException, ClassNotFoundException
        {

            int taskId = generateMapId();


            List<Integer> workerPorts;
            List<String> workerHosts;
            synchronized (activeWorkers) {
                if (activeWorkers.isEmpty()) {
                    outToClient.writeObject("ERROR: No workers to handle SEARCH");
                    outToClient.flush();
                    return;
                }
                workerPorts = new ArrayList<>(activeWorkers.keySet());
                workerHosts = new ArrayList<>();
                for (Integer port : workerPorts) {
                    workerHosts.add(activeWorkers.get(port));
                }
            }

            int expectedWorkers = workerPorts.size();
            System.out.println("[Master] For taskId=" + taskId
                    + " expectingWorkers=" + expectedWorkers);

            try (
                    Socket socketToReducer0 = new Socket("localhost", 9999);
                    ObjectOutputStream outToReducer0 = new ObjectOutputStream(socketToReducer0.getOutputStream());
                    ObjectInputStream  inFromReducer0 = new ObjectInputStream(socketToReducer0.getInputStream())
            ) {
                System.out.println("[Master] Sending to Reducer: MASTER;"
                        + taskId + ";" + expectedWorkers);
                outToReducer0.writeObject("MASTER");
                outToReducer0.writeInt(taskId);
                outToReducer0.writeInt(expectedWorkers);
                outToReducer0.flush();


                Object ack0 = inFromReducer0.readObject();
                if (ack0 instanceof String) {
                    System.out.println("[Master] Received from Reducer: " + ack0);
                }
            } catch (Exception e) {
                System.err.println("[Master][ERROR] Could not send MASTER to Reducer: "
                        + e.getMessage());
                e.printStackTrace();
                outToClient.writeObject("ERROR: Reduce initialization failed");
                outToClient.flush();
                return;
            }


            String mapCommand = "MAP:" + taskId + ";" + cmd;
            System.out.println("[Master] mapCommand = \"" + mapCommand + "\"");


            WorkersCoordinator coordinator = new WorkersCoordinator(expectedWorkers);

            for (int i = 0; i < workerPorts.size(); i++) {
                String whost = workerHosts.get(i);
                int wport = workerPorts.get(i);

                System.out.println("[Master] Δημιουργούμε thread για Worker " + whost + ":" + wport);
                WorkerSenderThread sender = new WorkerSenderThread(whost, wport, mapCommand, coordinator);
                sender.start();
            }


            try {
                coordinator.waitForAll();
                System.out.println("[Master] Όλοι οι Workers έστειλαν ACK (ή απέτυχαν). Προχωράμε...");
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                System.err.println("[Master] Interrupted while waiting for workers.");
            }


            try {
                Thread.sleep(100);  // 100ms συνήθως αρκούν
            } catch (InterruptedException ignore) {}


            List<Store> finalResult;
            try (
                    Socket socketToReducer = new Socket("localhost", 9999);
                    ObjectOutputStream outToReducer = new ObjectOutputStream(socketToReducer.getOutputStream());
                    ObjectInputStream  inFromReducer = new ObjectInputStream(socketToReducer.getInputStream())
            ) {
                System.out.println("[Master][DEBUG] Sending GET_RESULT for taskId=" + taskId);
                outToReducer.writeObject("GET_RESULT");
                outToReducer.writeInt(taskId);
                outToReducer.flush();

                Object raw = inFromReducer.readObject();
                if (raw instanceof List<?>) {
                    finalResult = (List<Store>) raw;
                    System.out.println("[Master][DEBUG] Received reduced result size="
                            + finalResult.size());
                } else {
                    System.err.println("[Master][ERROR] Expected List<Store> but got "
                            + raw.getClass().getSimpleName());
                    finalResult = new ArrayList<>();
                }
            } catch (Exception e) {
                System.err.println("[Master][ERROR] Could not get result from Reducer: "
                        + e.getMessage());
                e.printStackTrace();
                finalResult = new ArrayList<>();
            }


            String json = new ObjectMapper().writeValueAsString(finalResult);
            outToClient.writeObject(json);
        }


        private void handleReport(String cmdOriginal, ObjectOutputStream outToClient)
                throws IOException, ClassNotFoundException
        {

            List<Integer> workerPorts;
            List<String>  workerHosts;
            synchronized (activeWorkers) {
                if (activeWorkers.isEmpty()) {
                    outToClient.writeObject(Collections.emptyMap());
                    outToClient.flush();
                    return;
                }
                workerPorts = new ArrayList<>(activeWorkers.keySet());
                workerHosts = new ArrayList<>();
                for (Integer p : workerPorts) {
                    workerHosts.add(activeWorkers.get(p));
                }
            }

            int expectedWorkers = workerPorts.size();


            int taskId = generateMapId();

            try (
                    Socket socketToReducer0 = new Socket("localhost", 9999);
                    ObjectOutputStream outToReducer0 = new ObjectOutputStream(socketToReducer0.getOutputStream());
                    ObjectInputStream  inFromReducer0 = new ObjectInputStream(socketToReducer0.getInputStream())
            ) {
                System.out.println("[Master] Sending to Reducer: MASTER_REPORT;"
                        + taskId + ";" + expectedWorkers);
                outToReducer0.writeObject("MASTER_REPORT");
                outToReducer0.writeInt(taskId);
                outToReducer0.writeInt(expectedWorkers);
                outToReducer0.flush();


                Object ack0 = inFromReducer0.readObject();
                if (ack0 instanceof String) {
                    System.out.println("[Master] Received from Reducer: " + ack0);
                }
            } catch (Exception e) {
                System.err.println("[Master][ERROR] Could not send MASTER_REPORT to Reducer: "
                        + e.getMessage());
                e.printStackTrace();
                outToClient.writeObject(Collections.emptyMap());
                outToClient.flush();
                return;
            }


            String cmdWithId;
            if (cmdOriginal.startsWith("REPORT_BY_CATEGORY:")) {
                String rest = cmdOriginal.substring("REPORT_BY_CATEGORY:".length());
                cmdWithId = "REPORT_BY_CATEGORY:" + taskId + ";" + rest;
            } else if (cmdOriginal.startsWith("REPORT_BY_TYPE:")) {
                String rest = cmdOriginal.substring("REPORT_BY_TYPE:".length());
                cmdWithId = "REPORT_BY_TYPE:" + taskId + ";" + rest;
            } else if (cmdOriginal.startsWith("REPORT:productName=")){
                String rest = cmdOriginal.substring("REPORT:productName=".length());
                cmdWithId = "REPORT:productName=" + taskId + ";" + rest;
            }else {
                outToClient.writeObject(Collections.emptyMap());
                outToClient.flush();
                return;
            }
            System.out.println("[Master] cmdWithId = \"" + cmdWithId + "\"");


            WorkersCoordinator coordinator = new WorkersCoordinator(expectedWorkers);

            for (int i = 0; i < workerPorts.size(); i++) {
                String whost = workerHosts.get(i);
                int    wport = workerPorts.get(i);

                System.out.println("[Master] Δημιουργούμε thread REPORT για Worker " + whost + ":" + wport);
                WorkerSenderThread sender = new WorkerSenderThread(whost, wport, cmdWithId, coordinator);
                sender.start();
            }

            try {
                coordinator.waitForAll();
                System.out.println("[Master] Όλοι οι Workers έστειλαν ACK (ή απέτυχαν) για το REPORT_BY_*.");
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                System.err.println("[Master] Interrupted while waiting for report-workers.");
            }



            try {
                Thread.sleep(100);
            } catch (InterruptedException ignore) {}

            Map<String,Integer> finalReport;
            try (
                    Socket socketToReducer = new Socket("localhost", 9999);
                    ObjectOutputStream outToReducer = new ObjectOutputStream(socketToReducer.getOutputStream());
                    ObjectInputStream  inFromReducer = new ObjectInputStream(socketToReducer.getInputStream())
            ) {
                System.out.println("[Master] Sending GET_REPORT for taskId=" + taskId);
                outToReducer.writeObject("GET_REPORT");
                outToReducer.writeInt(taskId);
                outToReducer.flush();

                Object raw = inFromReducer.readObject();
                if (raw instanceof Map<?,?>) {
                    finalReport = (Map<String,Integer>) raw;
                    System.out.println("[Master] Received reduced Report size=" + finalReport.size());
                } else {
                    System.err.println("[Master][ERROR] Expected Map<String,Integer> but got "
                            + raw.getClass().getSimpleName());
                    finalReport = Collections.emptyMap();
                }
            } catch (Exception e) {
                System.err.println("[Master][ERROR] Could not get result from Reducer: " + e.getMessage());
                e.printStackTrace();
                finalReport = Collections.emptyMap();
            }


            outToClient.writeObject(finalReport);
            outToClient.flush();
            System.out.println("[Master] Sent aggregated report (size=" + finalReport.size() + ") to Client");
        }














        private void handleProductReport(String cmd, ObjectOutputStream out) throws IOException {
            String storeName = extractValue(cmd, "storeName", null);

            if (storeName == null || storeName.isEmpty()) {
                out.writeObject("Λάθος: Δεν δόθηκε σωστό storeName.");
                return;
            }

            List<Integer> workers = storeToWorkers.get(storeName);
            if (workers == null || workers.isEmpty()) {
                out.writeObject("Το κατάστημα δεν έχει ανατεθεί σε Workers.");
                return;
            }

            int primary = workers.get(0);
            int replica = (workers.size() > 1) ? workers.get(1) : -1;

            Object response = forwardCommandToWorker(cmd, primary);

            if (response instanceof Map<?, ?> map) {
                int total = ((Map<String, Integer>) map).getOrDefault("total", 0);
                if (total == 0 && replica != -1) {
                    System.out.println("[Master] Primary επέστρεψε 0 πωλήσεις. Δοκιμή στο Replica...");
                    response = forwardCommandToWorker(cmd, replica);
                }
            } else {
                if (replica != -1) {
                    System.out.println("[Master] Λάθος απάντηση από Primary. Δοκιμή στο Replica...");
                    response = forwardCommandToWorker(cmd, replica);
                }
            }

            out.writeObject(response);
        }


        private void handleCommandWithFailover(String cmd, ObjectOutputStream out) throws IOException {
            String storeName = extractValue(cmd, "storeName", "store");
            List<Integer> workers = storeToWorkers.get(storeName);

            if (workers == null || workers.isEmpty()) {
                out.writeObject("[Master] Δεν βρέθηκαν Workers για το κατάστημα " + storeName);
                return;
            }

            int primary = workers.get(0);
            int replica = (workers.size() > 1) ? workers.get(1) : -1;

            Object response = forwardCommandToWorker(cmd, primary);
            boolean executedOnPrimary = true;

            if (response.toString().contains("δεν απάντησε") || response.toString().contains("failed")) {
                if (replica != -1) {
                    System.out.println("[Master] Primary απέτυχε. Δοκιμή στο Replica: " + replica);
                    response = forwardCommandToWorker(cmd, replica);
                    executedOnPrimary = false;
                } else {
                    response = "[Master] Αποτυχία: Δεν υπάρχει διαθέσιμος replica για " + storeName;
                }
            }


            if (executedOnPrimary && cmd.startsWith("RATE:") && replica != -1) {
                System.out.println("[Master] Προωθώ RATE σε Replica " + replica + ": " + cmd);
                forwardCommandToWorker(cmd, replica);
            }

            if (executedOnPrimary && cmd.startsWith("ADD_PRODUCT:") && replica != -1) {

                Object maybeStore = forwardCommandToWorker(
                        "GET_STORE_STATUS:storeName=" + storeName,
                        primary
                );
                if (maybeStore instanceof Store) {
                    Store updatedStore = (Store) maybeStore;

                    calculatePriceCategory(updatedStore);

                    allStores.put(storeName, updatedStore);

                    System.out.println("[Master] Στέλνω ενημερωμένο Store (νέα priceCategory) στον Replica: " + replica);
                    sendStoreToWorker(updatedStore, replica);
                } else {

                    Store fallbackStore = allStores.get(storeName);
                    if (fallbackStore != null) {
                        System.out.println("[Master] (Fallback) Στέλνω παλιό Store στον Replica: " + replica);
                        sendStoreToWorker(fallbackStore, replica);
                    }
                }
            }


            if (executedOnPrimary && cmd.startsWith("BUY:") && replica != -1) {
                String productName = null;
                String[] parts = cmd.substring("BUY:".length()).split(";");
                for (String part : parts) {
                    String[] kv = part.split("=");
                    if (kv[0].equals("product")) productName = kv[1];
                }

                String reportCmd = "GET_TOTAL_SALES:storeName=" + storeName
                        + ";product=" + productName;
                Object reportResp = forwardCommandToWorker(reportCmd, primary);
                System.out.println("[Master] Report από Replica: "+reportResp);

                int totalSales = 0;
                if (reportResp instanceof Map<?, ?>) {
                    Map<String, Integer> reportMap = (Map<String, Integer>) reportResp;
                    totalSales = reportMap.getOrDefault("total", 0);
                }

                String syncData = "storeName=" + storeName + ";product=" + productName + ";sales=" + totalSales;
                System.out.println(totalSales);
                System.out.println("[Master] SYNC_UPDATE στο Replica: " + replica);
                forwardCommandToWorker("SYNC_UPDATE:" + syncData, replica);
            }

            out.writeObject(response);
        }



        private void handleStoreStatus(String cmd, ObjectOutputStream out) throws IOException {
            String storeName = extractValue(cmd, "storeName", null);
            List<Integer> workers = storeToWorkers.get(storeName);

            if (workers == null) {
                out.writeObject("Το κατάστημα δεν έχει ανατεθεί σε Workers.");
                return;
            }

            int primary = workers.get(0);

            Integer replica = (workers.size() > 1 ? workers.get(1) : null);

            Object response = forwardCommandToWorker(cmd, primary);


            if (replica != null && !(response instanceof Store)) {
                System.out.println("[Master] Primary απέτυχε ή δεν επέστρεψε Store. Δοκιμή στο Replica...");
                response = forwardCommandToWorker(cmd, replica);
            }

            out.writeObject(response);

        }


        private void calculatePriceCategory(Store store) {
            double sum = 0;
            for (Product p : store.getProducts())
                sum += p.getPrice();
            double avg = sum / store.getProducts().size();
            if (avg <= 5)
                store.setPriceCategory("$");
            else if (avg <= 15)
                store.setPriceCategory("$$");
            else
                store.setPriceCategory("$$$");
        }



        private void assignStoreToWorkers(String storeName) {

            List<Integer> allWorkers;
            synchronized(activeWorkers) {
                allWorkers = new ArrayList<>(activeWorkers.keySet());
            }

            if (allWorkers.isEmpty()) {
                throw new RuntimeException("No available workers for assignment of " + storeName);
            }


            List<WorkerLoad> loads = new ArrayList<>();
            synchronized(workerToStore) {
                for (int port : allWorkers) {
                    List<String> assigned = workerToStore.get(port);
                    int count = (assigned != null ? assigned.size() : 0);
                    loads.add(new WorkerLoad(port, count));
                }
            }

            loads.sort(Comparator.comparingInt(wl -> wl.load));


            int primaryPort = loads.get(0).port;


            Integer replicaPort = null;
            if (loads.size() > 1) {
                replicaPort = loads.get(1).port;
            }


            synchronized(workerToStore) {

                workerToStore
                        .computeIfAbsent(primaryPort, k -> new ArrayList<>())
                        .add(storeName);


                if (replicaPort != null) {
                    workerToStore
                            .computeIfAbsent(replicaPort, k -> new ArrayList<>())
                            .add(storeName);
                }
            }


            if (replicaPort == null) {
                storeToWorkers.put(storeName, List.of(primaryPort));
                System.out.println("[Master] Ανάθεση " + storeName +
                        " → Primary: " + primaryPort);
            } else {
                storeToWorkers.put(storeName, List.of(primaryPort, replicaPort));
                System.out.println("[Master] Ανάθεση " + storeName +
                        " → Primary: " + primaryPort +
                        ", Replica: " + replicaPort);
            }
        }


        static class WorkerLoad {
            int port;
            int load;

            WorkerLoad(int port, int load) {
                this.port = port;
                this.load = load;
            }
        }




        private boolean sendStoreToAllReplicas(Store store) {

            List<Integer> workers = storeToWorkers.get(store.getStoreName());
            if (workers == null || workers.isEmpty()) {
                // Αν δεν έχει assigned workers, επιστρέφουμε false
                return false;
            }


            WorkersCoordinator coordinator = new WorkersCoordinator(workers.size());


            List<Boolean> results = Collections.synchronizedList(new ArrayList<>());


            for (int port : workers) {
                String whost = getWorkerHost(port);
                System.out.println("[Master] Δημιουργούμε thread STORE για Worker " + whost + ":" + port);
                StoreSenderThread sender = new StoreSenderThread(whost, port, store, coordinator, results);
                sender.start();
            }


            try {
                coordinator.waitForAll();
                System.out.println("[Master] Όλοι οι Workers ολοκλήρωσαν αποστολή του Store.");
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                System.err.println("[Master] Interrupted while waiting for store-senders.");
            }


            synchronized (results) {
                for (Boolean b : results) {
                    if (!b) {
                        return false;
                    }
                }
            }
            return true;
        }















        private String extractValue(String cmd, String key, String alternativeKey) {

            String[] parts = cmd.split("[;:]");

            for (String part : parts) {
                part = part.trim();
                if (part.startsWith(key + "=")) {
                    return part.substring((key + "=").length()).trim();
                }
                if (alternativeKey != null && part.startsWith(alternativeKey + "=")) {
                    return part.substring((alternativeKey + "=").length()).trim();
                }
            }
            return null;
        }






    }

    private static Object forwardCommandToWorker(String command, int port) {
        String host = getWorkerHost(port);
        System.out.println("[Master] Opening connection to Worker on port " + port);
        try (Socket socket = new Socket(host, port);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            out.flush();
            out.writeObject(command);
            out.flush();
            Object response = in.readObject();

            System.out.println("[Master] Closing connection to Worker on port " + port);
            return response;

        } catch (Exception e) {
            System.err.println("[Master][ERROR] Worker on port " + port + " unreachable. Killing...");
            killWorker(port-5000);
            return "failed";
        }
    }



    private static String getWorkerHost(int port) {
        synchronized(activeWorkers) {
            return activeWorkers.get(port);
        }
    }

    private static boolean sendStoreToWorker(Store store, int port) {
        String host = getWorkerHost(port);
        try (Socket socket = new Socket(host, port);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            out.flush();
            out.writeObject(store);
            out.flush();
            Object response = in.readObject();
            // Επιστρέφει true αν το response περιέχει "επιτυχώς"
            return response instanceof String && ((String) response).toLowerCase().contains("επιτυχώς");
        } catch (Exception e) {
            System.err.println("[Master][ERROR] Worker on port " + port + " unreachable. Killing...");
            killWorker(port - BASE_WORKER_PORT);
            return false;
        }
    }

    private static synchronized int generateMapId() {
        return nextMapId++;
    }
    private static String killWorker(int workerId) {
        int port = BASE_WORKER_PORT + workerId;


        String host;
        synchronized (activeWorkers) {
            if (!activeWorkers.containsKey(port)) {
                String msg = "Worker " + workerId + " δεν είναι ενεργός ή έχει ήδη διαγραφεί.";
                System.out.println("[Master] " + msg);
                return msg;
            }
            host = activeWorkers.get(port);
            activeWorkers.remove(port);
        }


        synchronized (storeToWorkers) {
            for (Map.Entry<String, List<Integer>> entry : storeToWorkers.entrySet()) {
                String storeName = entry.getKey();
                List<Integer> currentPorts = new ArrayList<>(entry.getValue());


                if (!currentPorts.contains(port)) continue;

                System.out.println("[Master] Αφαίρεση Worker " + port + " από το store: " + storeName);


                releaseWorker(port, storeName);


                currentPorts.remove(Integer.valueOf(port));


                if (currentPorts.size() == 1) {
                    int newPrimaryPort = currentPorts.get(0);
                    System.out.println("[Master] Ο μόνος διαθέσιμος worker για " + storeName +
                            " είναι: " + newPrimaryPort + " => γίνεται primary");



                    int replicaPort = newPrimaryPort;


                    Object maybeStore = forwardCommandToWorker(
                            "GET_STORE_STATUS:storeName=" + storeName,
                            replicaPort
                    );

                    if (maybeStore instanceof Store) {
                        Store upToDateStore = (Store) maybeStore;

                        allStores.put(storeName, upToDateStore);


                        System.out.println("[Master] Στέλνω (ενημερωμένο) " + storeName +
                                " στον νέο primary " + newPrimaryPort);
                        sendStoreToWorker(upToDateStore, newPrimaryPort);
                    } else {

                        Store fallbackStore = allStores.get(storeName);
                        if (fallbackStore != null) {
                            System.out.println("[Master] (Fallback) Στέλνω παλιό " + storeName +
                                    " στον νέο primary " + newPrimaryPort);
                            sendStoreToWorker(fallbackStore, newPrimaryPort);
                        }
                    }
                }


                if (currentPorts.size() < 2) {
                    int newReplica = -1;
                    synchronized (activeWorkers) {
                        for (int candidate : activeWorkers.keySet()) {
                            if (!currentPorts.contains(candidate) && !workerToStore.containsKey(candidate)) {
                                newReplica = candidate;
                                break;
                            }
                        }
                    }
                    if (newReplica != -1) {
                        currentPorts.add(newReplica);
                        synchronized (workerToStore) {
                            workerToStore
                                    .computeIfAbsent(newReplica, k -> new ArrayList<>())
                                    .add(storeName);
                        }
                        System.out.println("[Master] Ανάθεση νέου replica για " + storeName +
                                ": " + newReplica);

                        Store upToDate = allStores.get(storeName);
                        if (upToDate != null) {
                            System.out.println("[Master] Στέλνω " + storeName +
                                    " στον νέο replica " + newReplica);
                            sendStoreToWorker(upToDate, newReplica);
                        }
                    } else {
                        System.out.println("[Master] Δεν βρέθηκε διαθέσιμος replica για " + storeName);
                    }
                }


                storeToWorkers.put(storeName, currentPorts);
            }
        }

        try (Socket socket = new Socket(host, port);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
            out.writeObject("TERMINATE");
            out.flush();
            System.out.println("[Master] Worker " + workerId + " ειδοποιήθηκε για τερματισμό.");
        } catch (IOException e) {
            System.err.println("[Master] Worker " + workerId +
                    " πιθανότατα ήδη έκλεισε ή δεν απαντά.");
        }

        return "Worker " + workerId + " has been killed.";
    }




    private static void releaseWorker(int workerPort, String storeName) {
        synchronized(workerToStore) {
            List<String> stores = workerToStore.get(workerPort);
            if (stores != null) {
                stores.remove(storeName);
                if (stores.isEmpty()) {
                    workerToStore.remove(workerPort);
                }
            }
        }
    }


}
