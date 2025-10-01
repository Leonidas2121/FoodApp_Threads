//StoreManager
package com.example.myapplicationnetwork;
import java.io.*;
import java.net.*;
import java.util.*;

public class StoreManager {
    private final String serverAddress;
    private final int serverPort;
    private final List<Store> managedStores = new ArrayList<>();

    public StoreManager(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
    }

    public Object sendCommandToServer(String command) {
        try (Socket socket = new Socket(this.serverAddress, this.serverPort);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            out.writeObject(command);
            out.flush();
            return in.readObject();

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Σφάλμα επικοινωνίας με τον server: " + e.getMessage());
            return "Σφάλμα: Δεν ήταν δυνατή η επικοινωνία με τον server.";
        }
    }

    public Object addStore(String storeJson, Store storeObj) {
        managedStores.add(storeObj);
        return sendCommandToServer(storeJson);
    }

    public Object updateStock(String storeName, String product, int amount) {
        String cmd = "UPDATE_STOCK:store=" + storeName + ";product=" + product + ";amount=" + amount;
        return sendCommandToServer(cmd);
    }
    public Object sendCustomCommand(String cmd) {
        return sendCommandToServer(cmd);
    }

    public Object addProduct(String storeName, String name, String type, int amount, double price) {
        String cmd = "ADD_PRODUCT:store=" + storeName + ";name=" + name + ";type=" + type + ";amount=" + amount + ";price=" + price;
        return sendCommandToServer(cmd);
    }

    public Object removeProduct(String storeName, String product) {
        String cmd = "REMOVE_PRODUCT:store=" + storeName + ";product=" + product;
        return sendCommandToServer(cmd);
    }

    public Object getSalesReportByProductName(String productName, String storeName) {
        String cmd = "REPORT:productName=" + productName + ";storeName=" + storeName;
        return sendCommandToServer(cmd);
    }


    public List<Store> getManagedStores() {
        return managedStores;
    }

    public boolean storeExists(String name) {
        return managedStores.stream().anyMatch(s -> s.getStoreName().equalsIgnoreCase(name));
    }

    public Store getStoreByName(String name) {
        return managedStores.stream().filter(s -> s.getStoreName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    public Store fetchStoreFromWorker(String storeName) {
        try (Socket socket = new Socket(this.serverAddress, this.serverPort);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            String request = "GET_STORE_STATUS:storeName=" + storeName;
            out.writeObject(request);
            out.flush();

            Object response = in.readObject();
            if (response instanceof Store store) {
                return store;
            } else {
                System.out.println("Λάθος απάντηση από τον server για " + storeName);
            }

        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Σφάλμα κατά την επικοινωνία για το κατάστημα: " + storeName);
            e.printStackTrace();
        }
        return null;
    }

}