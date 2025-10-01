//Product
package com.example.myapplicationnetwork;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

public class Product implements Serializable {
    private static final long serialVersionUID = 1L;
    @JsonProperty("ProductName")
    private String productName;

    @JsonProperty("ProductType")
    private String productType;

    @JsonProperty("AvailableAmount")
    private int availableAmount;

    @JsonProperty("Price")
    private double price;

    public Product() {}

    public Product(String name, String type, int amount, double price) {
        this.productName = name;
        this.productType = type;
        this.availableAmount = amount;
        this.price = price;
    }






    // Getters & Setters
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }
    public int getAvailableAmount() { return availableAmount; }
    public void setAvailableAmount(int availableAmount) { this.availableAmount = availableAmount; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
}
