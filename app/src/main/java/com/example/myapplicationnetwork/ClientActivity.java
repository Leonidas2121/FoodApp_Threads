package com.example.myapplicationnetwork;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;


public class ClientActivity extends AppCompatActivity {

    private FrameLayout framePizzeria, frameMexican, frameBurger;
    private ImageView imgPizzeria, imgMexican, imgBurger;
    private ImageView checkPizzeria, checkMexican, checkBurger;


    private boolean isPizzeriaSelected = false;
    private boolean isMexicanSelected = false;
    private boolean isBurgerSelected = false;


    private List<String> selectedCategories = new ArrayList<>();


    private RatingBar minStarsRating;
    private int minStars = 0;


    private ImageView price_1_1;
    private ImageView price_2_1, price_2_2;
    private ImageView price_3_1, price_3_2, price_3_3;


    private LinearLayout group_price_1;
    private LinearLayout group_price_2;
    private LinearLayout group_price_3;


    private int selectedPriceLevel = 0;


    private Button searchButton, backButton;
    private RecyclerView storeRecyclerView;
    private StoreAdapter adapter;

    private EditText editLatitude, editLongitude;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client);


        framePizzeria = findViewById(R.id.framePizzeria);
        frameMexican  = findViewById(R.id.frameMexican);
        frameBurger   = findViewById(R.id.frameBurger);

        imgPizzeria   = findViewById(R.id.imgPizzeria);
        imgMexican    = findViewById(R.id.imgMexican);
        imgBurger     = findViewById(R.id.imgBurger);

        checkPizzeria = findViewById(R.id.checkPizzeria);
        checkMexican  = findViewById(R.id.checkMexican);
        checkBurger   = findViewById(R.id.checkBurger);


        minStarsRating   = findViewById(R.id.minStarsRating);


        price_1_1  = findViewById(R.id.price_1_1);
        price_2_1  = findViewById(R.id.price_2_1);
        price_2_2  = findViewById(R.id.price_2_2);
        price_3_1  = findViewById(R.id.price_3_1);
        price_3_2  = findViewById(R.id.price_3_2);
        price_3_3  = findViewById(R.id.price_3_3);


        group_price_1 = findViewById(R.id.group_price_1);
        group_price_2 = findViewById(R.id.group_price_2);
        group_price_3 = findViewById(R.id.group_price_3);

        editLatitude = findViewById(R.id.editLatitude);
        editLongitude = findViewById(R.id.editLongitude);


        searchButton     = findViewById(R.id.searchButton);
        backButton       = findViewById(R.id.btnBack);
        storeRecyclerView = findViewById(R.id.recyclerView);

        adapter = new StoreAdapter(this);
        storeRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        storeRecyclerView.setAdapter(adapter);

        framePizzeria.setOnClickListener(v -> {
            isPizzeriaSelected = !isPizzeriaSelected;
            if (isPizzeriaSelected) {
                imgPizzeria.setAlpha(0.5f);
                checkPizzeria.setVisibility(View.VISIBLE);
                if (!selectedCategories.contains("pizzeria")) {
                    selectedCategories.add("pizzeria");
                }
            } else {
                imgPizzeria.setAlpha(1.0f);
                checkPizzeria.setVisibility(View.GONE);
                selectedCategories.remove("pizzeria");
            }
        });

        frameMexican.setOnClickListener(v -> {
            isMexicanSelected = !isMexicanSelected;
            if (isMexicanSelected) {
                imgMexican.setAlpha(0.5f);
                checkMexican.setVisibility(View.VISIBLE);
                if (!selectedCategories.contains("mexican")) {
                    selectedCategories.add("mexican");
                }
            } else {
                imgMexican.setAlpha(1.0f);
                checkMexican.setVisibility(View.GONE);
                selectedCategories.remove("mexican");
            }
        });

        frameBurger.setOnClickListener(v -> {
            isBurgerSelected = !isBurgerSelected;
            if (isBurgerSelected) {
                imgBurger.setAlpha(0.5f);
                checkBurger.setVisibility(View.VISIBLE);
                if (!selectedCategories.contains("burger")) {
                    selectedCategories.add("burger");
                }
            } else {
                imgBurger.setAlpha(1.0f);
                checkBurger.setVisibility(View.GONE);
                selectedCategories.remove("burger");
            }
        });


        minStarsRating.setOnRatingBarChangeListener((ratingBar, rating, fromUser) -> {
            if (fromUser) {
                minStars = (int) rating;
            }
        });


        group_price_1.setOnClickListener(v -> updatePriceIcons(1));
        group_price_2.setOnClickListener(v -> updatePriceIcons(2));
        group_price_3.setOnClickListener(v -> updatePriceIcons(3));


        updatePriceIcons(0);


        searchButton.setOnClickListener(v -> searchStores());


        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
        });


        searchStores();
    }


    private void updatePriceIcons(int level) {
        selectedPriceLevel = level;


        int colorGrey = getResources().getColor(R.color.starGrey);
        price_1_1.setColorFilter(colorGrey);
        price_2_1.setColorFilter(colorGrey);
        price_2_2.setColorFilter(colorGrey);
        price_3_1.setColorFilter(colorGrey);
        price_3_2.setColorFilter(colorGrey);
        price_3_3.setColorFilter(colorGrey);

        // 2) Βάφουμε πράσινα (starGold) μόνο την αντίστοιχη ομάδα:
        int colorGrenn = getResources().getColor(R.color.green);
        if (level == 1) {
            price_1_1.setColorFilter(colorGrenn);
        } else if (level == 2) {
            price_2_1.setColorFilter(colorGrenn);
            price_2_2.setColorFilter(colorGrenn);
        } else if (level == 3) {
            price_3_1.setColorFilter(colorGrenn);
            price_3_2.setColorFilter(colorGrenn);
            price_3_3.setColorFilter(colorGrenn);
        }
    }

    private void searchStores() {

        String category;
        if (selectedCategories.isEmpty()) {
            category = "";
        } else {
            StringJoiner joiner = new StringJoiner(",");
            for (String cat : selectedCategories) {
                joiner.add(cat);
            }
            category = joiner.toString();
        }


        String stars = String.valueOf(minStars);


        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < selectedPriceLevel; i++) {
            sb.append("$");
        }
        String price = sb.toString();


        double latitude = 0;
        double longitude = 0;
        try {
            String latText = editLatitude.getText().toString().trim();
            String lonText = editLongitude.getText().toString().trim();
            if (!latText.isEmpty()) latitude = Double.parseDouble(latText);
            if (!lonText.isEmpty()) longitude = Double.parseDouble(lonText);
        } catch (NumberFormatException e) {

        }



        String fullCommand = "SEARCH:category=" + category
                + ";stars=" + stars
                + ";price=" + price
                + ";latitude=" + latitude
                + ";longitude=" + longitude;


        Log.d("DEBUG", "Θέτω fullCommand=\"" + fullCommand + "\"");


        new Thread(() -> {
            try (Socket socket = new Socket("10.0.2.2", 4321);
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                out.writeObject(fullCommand);
                out.flush();


                String response = (String) in.readObject();
                List<Store> storesFromServer = new ObjectMapper()
                        .readValue(response, new TypeReference<List<Store>>() {});

                // Debug log
                Log.d("DEBUG", "Τύπος απόκρισης: "
                        + (response != null ? response.getClass().getName() : "null"));
                Log.d("DEBUG", "Περιεχόμενο: " + response);

                // 7. Ενημερώνουμε τον adapter
                runOnUiThread(() -> adapter.updateData(storesFromServer));

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this,
                                "Αποτυχία σύνδεσης ή λήψης δεδομένων",
                                Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }
}
