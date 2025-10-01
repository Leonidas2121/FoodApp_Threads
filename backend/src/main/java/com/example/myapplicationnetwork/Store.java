//Store
package com.example.myapplicationnetwork;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.*;
@JsonIgnoreProperties(ignoreUnknown = true)
public class Store implements Serializable {
    private static final long serialVersionUID = -5239021324177443673L;
    @JsonProperty("StoreName")
    private String storeName;
    @JsonProperty("Latitude")
    private double latitude;
    @JsonProperty("Longitude")
    private double longitude;
    @JsonProperty("FoodCategory")
    private String foodCategory;
    @JsonProperty("Stars")
    private double stars;
    @JsonProperty("NoOfVotes")
    private int noOfVotes;
    @JsonProperty("StoreLogo")
    private String storeLogo;
    @JsonProperty("Products")
    private List<Product> products;
    private String priceCategory;
    private final Map<String, Integer> salesByProductName = new HashMap<>();
    private double totalRating = 0; // Προσθέτουμε για τον υπολογισμό του μέσου όρου

    public synchronized boolean registerSale(String productName, int quantity) {
        System.out.println("[Store] Attempting to register sale: product=" + productName + ", qty=" + quantity + " in store=" + storeName); // Debug

        for (Product p : products) {
            System.out.println("[Store] Checking product: " + p.getProductName()); // Debug
            if (p.getProductName().equalsIgnoreCase(productName)) {
                System.out.println("[Store] Product found. Available amount: " + p.getAvailableAmount()); // Debug
                if (p.getAvailableAmount() >= quantity) {
                    p.setAvailableAmount(p.getAvailableAmount() - quantity);



                    String normalized = productName.trim().toLowerCase();

                    salesByProductName.merge(normalized, quantity, Integer::sum);


                    System.out.println("[Store] Sale successful. Remaining amount: " + p.getAvailableAmount()); // Debug
                    return true;
                } else {
                    System.out.println("[Store] Insufficient stock."); // Debug
                    return false;
                }
            }
        }
        System.out.println("[Store] Product not found."); // Debug
        return false;
    }

    public synchronized boolean updateStock(String productName, int amount) {
        for (Product p : products) {
            if (p.getProductName().equalsIgnoreCase(productName)) {
                p.setAvailableAmount(p.getAvailableAmount() + amount);
                return true;
            }
        }
        return false;
    }
    /**
     * Επιστρέφει το πλήθος πωλήσεων για προϊόντα του δεδομένου τύπου
     */
    public int getSalesByProductType(String type) {
        int total = 0;
        // Υποθέτουμε ότι η κλαση Product έχει getProductType() και getQuantitySold()
        for (Product p : this.products) {
            if (p.getProductType().equalsIgnoreCase(type)) {
                total += getSalesByProductName(p.getProductName());
            }
        }
        return total;
    }


    public synchronized boolean addProduct(String name, String type, int amount, double price) {
        for (Product p : products) {
            if (p.getProductName().equalsIgnoreCase(name)) {
                return false;
            }
        }
        products.add(new Product(name, type, amount, price));
        return true;
    }

    public synchronized boolean removeProduct(String productName) {
        return products.removeIf(p -> p.getProductName().equalsIgnoreCase(productName));
    }

    public synchronized void syncStockOnly(String productName, int quantity) {
        for (Product p : products) {
            if (p.getProductName().equalsIgnoreCase(productName)) {
                p.setAvailableAmount(p.getAvailableAmount() - quantity);
                break;
            }
        }
    }




    public int getSalesByProductName(String name) {
        String normalized = name.trim().toLowerCase();
        return salesByProductName.getOrDefault(normalized, 0);
    }


    public int getTotalSales() {
        return salesByProductName.values().stream().mapToInt(i -> i).sum();
    }


    public synchronized void addRating(int rating) {
        totalRating = getStars() * noOfVotes;
        if (rating >= 1 && rating <= 5) { // Υποθέτουμε κλίμακα 1-5
            totalRating += rating;
            noOfVotes++;
            stars =  Math.round((double) totalRating / noOfVotes); // Ενημερώνουμε τα αστέρια
            System.out.println("[Store] Stars: " + stars);
        }
    }

    public synchronized void syncSale(String productName, int absoluteQuantity) {
        String normalized = productName.trim().toLowerCase();
        salesByProductName.put(normalized, absoluteQuantity); // ΣΩΣΤΟ!
    }





    // Getters και Setters
    public double getTotalRating() { return totalRating; }
    public void setTotalRating(int totalRating) { this.totalRating = totalRating; }
    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public String getFoodCategory() { return foodCategory; }
    public void setFoodCategory(String foodCategory) { this.foodCategory = foodCategory; }
    public double getStars() { return stars; }
    public void setStars(int stars) { this.stars = stars; }
    public int getNoOfVotes() { return noOfVotes; }
    public void setNoOfVotes(int noOfVotes) { this.noOfVotes = noOfVotes; }
    public String getStoreLogo() { return storeLogo; }
    public void setStoreLogo(String storeLogo) { this.storeLogo = storeLogo; }
    public List<Product> getProducts() { return products; }
    public void setProducts(List<Product> products) { this.products = products; }
    public String getPriceCategory() { return priceCategory; }
    public void setPriceCategory(String priceCategory) { this.priceCategory = priceCategory; }

    @Override
    public String toString() {
        return String.format(
                "Store[name=%s, category=%s, stars=%.1f, price=%s, lat=%.6f, lon=%.6f]",
                getStoreName(),
                getFoodCategory(),
                getStars(),
                getPriceCategory(),
                getLatitude(),
                getLongitude()
        );
    }

}
