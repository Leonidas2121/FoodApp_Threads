package com.example.myapplicationnetwork;
import java.io.*;
import java.net.*;
import java.util.*;

public class DummyClient {
    public static void main(String[] args) {
        String serverAddress = "localhost";
        int serverPort = 4321;
        Scanner scanner = new Scanner(System.in);

        List<?> stores = new ArrayList<>();
        double userLatitude = 0.0;
        double userLongitude = 0.0;

        while (stores.isEmpty()) {
            try (Socket socket = new Socket(serverAddress, serverPort);
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                System.out.println("--- Dummy Client ---");
                System.out.print("Δώσε κατηγορία φαγητού (πχ pizzeria): ");
                String category = scanner.nextLine();

                System.out.print("Δώσε ελάχιστα αστέρια (πχ 4): ");
                String stars = scanner.nextLine();

                System.out.print("Δώσε κατηγορία τιμής ($, $$, $$$): ");
                String price = scanner.nextLine();

                System.out.print("Δώσε το γεωγραφικό πλάτος σου: ");
                userLatitude = Double.parseDouble(scanner.nextLine());

                System.out.print("Δώσε το γεωγραφικό μήκος σου: ");
                userLongitude = Double.parseDouble(scanner.nextLine());


                String searchRequest = "SEARCH:category=" + category + ";stars=" + stars + ";price=" + price +
                        ";latitude=" + userLatitude + ";longitude=" + userLongitude;
                out.writeObject(searchRequest);
                out.flush();

                Object response = in.readObject();
                if (response instanceof List<?>) {
                    stores = (List<?>) response;
                    if (stores.isEmpty()) {
                        System.out.println("\nΔεν βρέθηκαν καταστήματα με αυτά τα φίλτρα.");
                        System.out.print("Πάτα 0 για έξοδο ή Enter για νέα αναζήτηση: ");
                        String choice = scanner.nextLine();
                        if (choice.equals("0")) return;
                        continue;
                    }
                } else {
                    System.out.println("Σφάλμα στην απάντηση του server.");
                    return;
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
                return;
            }
        }

        Map<String, Map<String, Integer>> storeProductStock = new HashMap<>();
        System.out.println("\nΚαταστήματα που βρέθηκαν:");
        for (Object obj : stores) {
            if (obj instanceof Store store) {
                System.out.println("- " + store.getStoreName());
                System.out.println("  Προϊόντα:");
                Map<String, Integer> productMap = new HashMap<>();
                store.getProducts().forEach(p -> {
                    System.out.println("    - " + p.getProductName() + " (" + p.getAvailableAmount() + " διαθέσιμα, " + p.getPrice() + "€)");
                    productMap.put(p.getProductName(), Integer.valueOf(p.getAvailableAmount()));
                });
                storeProductStock.put(store.getStoreName(), productMap);
            }
        }

        System.out.print("\nΘες να αγοράσεις από κάποιο κατάστημα; (yes/no): ");
        String answer = scanner.nextLine();
        while(!answer.equalsIgnoreCase("yes") && !answer.equalsIgnoreCase("no")){
            System.out.print("\nΜη έγκυρη απάντηση");
            System.out.print("\nΘες να αγοράσεις από κάποιο κατάστημα; (yes/no): ");
            Scanner in = new Scanner(System.in);
            answer = in.nextLine();

        }

        if (answer.equalsIgnoreCase("yes")) {
            String storeName;
            while (true) {
                System.out.print("Δώσε όνομα καταστήματος: ");
                storeName = scanner.nextLine();
                if (storeProductStock.containsKey(storeName)) break;
                System.out.println("Το κατάστημα δεν βρέθηκε στη λίστα αποτελεσμάτων. Προσπάθησε ξανά.");
            }

            List<String> orders  = new ArrayList<>();

            // Loop για εισαγωγή προϊόντων
            while (true) {
                String product;
                while (true) {
                    System.out.print("Δώσε προϊόν (ή 'stop' για ολοκλήρωση): ");
                    product = scanner.nextLine();
                    if (product.equalsIgnoreCase("stop")) break;
                    if (storeProductStock.get(storeName).containsKey(product)) break;
                    System.out.println("Το προϊόν δεν υπάρχει στο κατάστημα. Προσπάθησε ξανά.");
                }
                if (product.equalsIgnoreCase("stop")) break;

                int qty;
                int available = storeProductStock.get(storeName).get(product);
                while (true) {
                    System.out.print("Δώσε ποσότητα: ");
                    qty = Integer.parseInt(scanner.nextLine());
                    if (qty > 0 && qty <= available) break;
                    System.out.println("Μη έγκυρη ποσότητα. Διαθέσιμη: " + available);
                }

                orders.add("BUY:storeName=" + storeName + ";product=" + product + ";quantity=" + qty);
            }


            for (String buyRequest : orders ) {
                try (Socket buySocket = new Socket(serverAddress, serverPort);
                     ObjectOutputStream buyOut = new ObjectOutputStream(buySocket.getOutputStream());
                     ObjectInputStream buyIn = new ObjectInputStream(buySocket.getInputStream())) {

                    buyOut.writeObject(buyRequest);
                    buyOut.flush();

                    Object buyResponse = buyIn.readObject();
                    System.out.println("Απάντηση αγοράς: " + buyResponse);


                } catch (IOException | ClassNotFoundException e) {
                    System.out.println("Σφάλμα κατά την αποστολή αγοράς.");
                    e.printStackTrace();
                }
            }

            System.out.print("\nΘέλετε να βαθμολογήσετε το κατάστημα " + storeName + "; (yes/no): ");
            String rateAnswer = scanner.nextLine();
            while(!rateAnswer.equalsIgnoreCase("yes") && !rateAnswer.equalsIgnoreCase("no")){
                System.out.print("\nΜη έγκυρη απάντηση. (yes/no): ");
                rateAnswer = scanner.nextLine();
            }
            if (rateAnswer.equalsIgnoreCase("yes")) {
                int rating;
                while (true) {
                    System.out.print("Δώστε βαθμολογία (1-5): ");
                    rating = Integer.parseInt(scanner.nextLine());
                    if (rating >= 1 && rating <= 5) break;
                    System.out.println("Μη έγκυρη βαθμολογία.");
                }

                try (Socket rateSocket = new Socket(serverAddress, serverPort);
                     ObjectOutputStream rateOut = new ObjectOutputStream(rateSocket.getOutputStream());
                     ObjectInputStream rateIn = new ObjectInputStream(rateSocket.getInputStream())) {

                    String rateRequest = "RATE:storeName=" + storeName + ";rating=" + rating;
                    rateOut.writeObject(rateRequest);
                    rateOut.flush();

                    Object rateResponse = rateIn.readObject();
                    System.out.println("Απάντηση βαθμολόγησης: " + rateResponse);

                } catch (IOException | ClassNotFoundException e) {
                    System.out.println("Σφάλμα κατά την αποστολή βαθμολογίας.");
                    e.printStackTrace();
                }
            }
        }

    }
}