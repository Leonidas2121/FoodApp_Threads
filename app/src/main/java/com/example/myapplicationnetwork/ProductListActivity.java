package com.example.myapplicationnetwork;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuItem;
import androidx.appcompat.widget.Toolbar;
import android.content.Intent;



public class ProductListActivity extends AppCompatActivity {
    private Store store;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_list);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Προϊόντα");


        RecyclerView recyclerView = findViewById(R.id.productRecycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        store = (Store) getIntent().getSerializableExtra("store");
        if (store == null || store.getProducts() == null) {
            Toast.makeText(this, "Δεν βρέθηκαν προϊόντα.", Toast.LENGTH_SHORT).show();
            return;
        }

        ProductAdapter adapter = new ProductAdapter(store.getProducts(), store.getStoreName(), this);

        recyclerView.setAdapter(adapter);
        Button backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_products, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_cart) {
            Intent intent = new Intent(this, CartActivity.class);

            intent.putExtra("store", store);

            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }



}
