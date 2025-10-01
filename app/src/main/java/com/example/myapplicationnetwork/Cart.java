package com.example.myapplicationnetwork;

import java.util.ArrayList;
import java.util.List;

public class Cart {
    private static Cart instance;
    private List<CartItem> items;

    private Cart() {
        items = new ArrayList<>();
    }

    public static synchronized Cart getInstance() {
        if (instance == null) {
            instance = new Cart();
        }
        return instance;
    }

    public void addProduct(Product product, int quantity) {
        for (CartItem ci : items) {
            if (ci.getProduct().getProductName().equals(product.getProductName())) {
                ci.setQuantity(ci.getQuantity() + quantity);
                return;
            }
        }
        items.add(new CartItem(product, quantity));
    }

    public List<CartItem> getItems() {
        return items;
    }

    public void clear() {
        items.clear();
    }
}

