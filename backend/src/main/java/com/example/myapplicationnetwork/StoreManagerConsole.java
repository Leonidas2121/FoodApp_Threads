//StoreManagerConsole
package com.example.myapplicationnetwork;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import com.fasterxml.jackson.databind.ObjectMapper;

public class StoreManagerConsole {

    private static void handleResponse(Object response) {
        if (response instanceof Map<?, ?> map) {
            System.out.println("\n--- Αποτελέσματα Αναφοράς ---");
            int total = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if ("total".equals(entry.getKey()) && entry.getValue() instanceof Integer) {
                    total = (Integer) entry.getValue();
                } else {
                    System.out.println(entry.getKey() + ": " + entry.getValue());
                }
            }
            if (map.containsKey("total")) {
                System.out.println("--------------------");
                System.out.println("Σύνολο: " + total);
            }
            System.out.println("---------------------------");
        } else if (response instanceof String message) {
            System.out.println("\nΑπάντηση Server: " + message);
        } else {
            System.out.println("\nΆγνωστος τύπος απάντησης: " + response);
        }
    }

    private static void printStoreDetails(Store store) {
        System.out.println("\nΚατάστημα: " + store.getStoreName());
        System.out.println(" - Κατηγορία: " + store.getFoodCategory());
        System.out.println(" - Αστέρια: " + store.getStars() + " (" + store.getNoOfVotes() + " ψήφοι)");
        System.out.println(" - Τοποθεσία: lat=" + store.getLatitude() + ", lon=" + store.getLongitude());
        System.out.println(" - Τιμολογιακή Κατηγορία: " + store.getPriceCategory());
        System.out.println(" - Προϊόντα:");
        store.getProducts().forEach(p ->
                System.out.println("   * " + p.getProductName() + " (" + p.getProductType() + ") - " + p.getAvailableAmount() + " διαθέσιμα, " + p.getPrice() + "€")
        );
    }


    public static void main(String[] args) {
        String serverAddress = (args.length >= 1) ? args[0] : "localhost"; // default: localhost
        int serverPort = 4321;
        Scanner scanner = new Scanner(System.in);
        StoreManager manager = new StoreManager(serverAddress, serverPort);

        while (true) {
            System.out.println("\n--- Store Manager Console ---");
            System.out.println("1. Προσθήκη καταστήματος από JSON");
            System.out.println("2. Ενημέρωση αποθέματος προϊόντος");
            System.out.println("3. Προσθήκη νέου προϊόντος");
            System.out.println("4. Αφαίρεση προϊόντος");
            System.out.println("5. Προβολή πωλήσεων για συγκεκριμένο προϊόν (όνομα)");
            System.out.println("6. Πωλήσεις ανά κατηγορία καταστήματος (FoodCategory)");
            System.out.println("7. Πωλήσεις ανά τύπο προϊόντος (ProductType)");
            System.out.println("8. Εμφάνιση όλων των καταστημάτων με λεπτομέρειες");
            System.out.println("9. Διαγραφή Worker");
            System.out.println("0. Έξοδος");

            System.out.print("Επιλογή: ");
            String choice = scanner.nextLine();
            Object response = null;

            try {
                switch (choice) {
                    case "1" -> {
                        System.out.print("Δώσε path JSON αρχείου: ");
                        String path = scanner.nextLine();
                        try {
                            String storeJson = new String(Files.readAllBytes(Paths.get(path)));
                            ObjectMapper mapper = new ObjectMapper();
                            Store store = mapper.readValue(storeJson, Store.class);
                            response = manager.addStore(storeJson, store);
                        } catch (IOException e) {
                            System.out.println("Σφάλμα στο διάβασμα αρχείου: " + e.getMessage());
                        }
                    }
                    case "2", "3", "4" -> {
                        if (manager.getManagedStores().isEmpty()) {
                            System.out.println("Δεν έχετε καταχωρήσει ακόμα καταστήματα.");
                            break;
                        }
                        System.out.println("Καταστήματα:");
                        for (Store s : manager.getManagedStores()) {
                            System.out.println("- " + s.getStoreName());
                        }
                        System.out.print("Δώσε όνομα καταστήματος: ");
                        String storeName = scanner.nextLine();
                        if (!manager.storeExists(storeName)) {
                            System.out.println("Δεν υπάρχει αυτό το κατάστημα.");
                            break;
                        }
                        if (choice.equals("2")) {
                            System.out.print("Προϊόν: ");
                            String product = scanner.nextLine();
                            System.out.print("Αλλαγή ποσότητας (π.χ. 10 ή -5): ");
                            int amount = Integer.parseInt(scanner.nextLine());


                            Store store = manager.fetchStoreFromWorker(storeName);
                            if (store == null) {
                                System.out.println("Αποτυχία ανάκτησης του καταστήματος.");
                                break;
                            }

                            int currentStock = -1;
                            for (Product p : store.getProducts()) {
                                if (p.getProductName().equalsIgnoreCase(product)) {
                                    currentStock = p.getAvailableAmount();
                                    break;
                                }
                            }

                            if (currentStock == -1) {
                                System.out.println("Το προϊόν δεν βρέθηκε στο κατάστημα.");
                                break;
                            }


                            if (currentStock + amount < 0) {
                                System.out.println("Η αλλαγή ποσότητας οδηγεί σε αρνητικό απόθεμα. Διαθέσιμο: " + currentStock);
                                break;
                            }

                            response = manager.updateStock(storeName, product, amount);
                        }else if (choice.equals("3")) {
                            System.out.print("Όνομα προϊόντος: ");
                            String name = scanner.nextLine();
                            System.out.print("Τύπος προϊόντος: ");
                            String type = scanner.nextLine();
                            System.out.print("Διαθέσιμη ποσότητα: ");
                            int amount = Integer.parseInt(scanner.nextLine());
                            System.out.print("Τιμή: ");
                            double price = Double.parseDouble(scanner.nextLine());
                            response = manager.addProduct(storeName, name, type, amount, price);
                        } else if (choice.equals("4")) {
                            System.out.print("Προϊόν προς αφαίρεση: ");
                            String product = scanner.nextLine();
                            response = manager.removeProduct(storeName, product);
                        }
                    }
                    case "5" -> {
                        System.out.print("Όνομα προϊόντος για αναφορά πωλήσεων: ");
                        String productName = scanner.nextLine().trim();

                        System.out.print("Όνομα καταστήματος: ");
                        String storeName = scanner.nextLine().trim();


                        String cmd = "REPORT:productName=" + productName + ";storeName=" + storeName;


                        Object response5 = manager.sendCommandToServer(cmd);

                        if (response5 instanceof Map<?, ?>) {

                            Map<String, Integer> salesByStore = (Map<String, Integer>) response5;

                            if (salesByStore.isEmpty()) {
                                System.out.println("Δεν βρέθηκαν πωλήσεις για προϊόν=\""
                                        + productName + "\" στο κατάστημα=\""
                                        + storeName + "\".");
                            } else {
                                System.out.println("\nΠωλήσεις για προϊόν=\""
                                        + productName + "\" στο κατάστημα=\""
                                        + storeName + "\":");
                                salesByStore.forEach((sName, sales) ->
                                        System.out.printf("  • %s → %d πωλήσεις%n", sName, sales)
                                );
                            }
                        } else {

                            System.out.println("Απάντηση από server: " + response5);
                        }
                    }

                    case "6" -> {
                        System.out.print("Δώσε FoodCategory (π.χ. pizzeria): ");
                        String category = scanner.nextLine().trim();


                        String cmd = "REPORT_BY_CATEGORY:FoodCategory=" + category;


                        response = manager.sendCommandToServer(cmd);

                        if (response instanceof Map<?, ?>) {

                            Map<String, Integer> salesByStore = (Map<String, Integer>) response;

                            if (salesByStore.isEmpty()) {
                                System.out.println("Δεν βρέθηκαν πωλήσεις για FoodCategory=\"" + category + "\".");
                            } else {
                                System.out.println("\nΠωλήσεις για FoodCategory=\"" + category + "\":");
                                salesByStore.forEach((storeName, sales) ->
                                        System.out.printf("  • %s → %d πωλήσεις%n", storeName, sales)
                                );
                            }
                        } else {

                            System.out.println("Απάντηση από server: " + response);
                        }
                    }

                    case "7" -> {
                        System.out.print("Δώσε ProductType (π.χ. pizza, salad): ");
                        String type = scanner.nextLine().trim();


                        String cmd2 = "REPORT_BY_TYPE:ProductType=" + type;


                        Object response2 = manager.sendCommandToServer(cmd2);

                        if (response2 instanceof Map<?, ?>) {

                            Map<String, Integer> salesByStore = (Map<String, Integer>) response2;

                            if (salesByStore.isEmpty()) {
                                System.out.println("Δεν βρέθηκαν πωλήσεις για ProductType=\"" + type + "\".");
                            } else {
                                System.out.println("\nΠωλήσεις για ProductType=\"" + type + "\":");
                                salesByStore.forEach((storeName, sales) ->
                                        System.out.printf("  • %s → %d πωλήσεις%n", storeName, sales)
                                );
                            }
                        } else {

                            System.out.println("Απάντηση από server: " + response2);
                        }
                    }


                    case "8" -> {
                        if (manager.getManagedStores().isEmpty()) {
                            System.out.println("Δεν υπάρχουν καταστήματα.");
                        } else {
                            for (Store store : manager.getManagedStores()) {
                                Store updatedStore = manager.fetchStoreFromWorker(store.getStoreName());
                                if (updatedStore != null) {
                                    printStoreDetails(updatedStore);
                                } else {
                                    System.out.println("Αδυναμία ανάκτησης στοιχείων για το κατάστημα: " + store.getStoreName());
                                }
                            }
                        }
                    }
                    case "9" -> {
                        System.out.print("Δώσε ID Worker για διαγραφή: ");
                        String input = scanner.nextLine().trim();
                        try {
                            int workerId = Integer.parseInt(input);
                            String cmd = "KILL_WORKER:workerId=" + workerId;
                            response = manager.sendCustomCommand(cmd);  // ✅ η απάντηση θα περιέχει επιτυχία ή μήνυμα αποτυχίας
                        } catch (NumberFormatException e) {
                            System.out.println("Μη έγκυρο ID Worker. Πρέπει να είναι αριθμός.");
                        }
                    }

                    case "0" -> {
                        System.out.println("Έξοδος.");
                        scanner.close();
                        return;
                    }
                    default -> System.out.println("Μη έγκυρη επιλογή. Δοκιμάστε ξανά.");
                }

                if (response != null) {
                    handleResponse(response);
                }

            } catch (NumberFormatException e) {
                System.out.println("Σφάλμα: Μη έγκυρος αριθμός εισόδου.");
            } catch (Exception e) {
                System.out.println("Προέκυψε μη αναμενόμενο σφάλμα: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
