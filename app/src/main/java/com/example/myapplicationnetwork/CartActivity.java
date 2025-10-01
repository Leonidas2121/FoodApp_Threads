package com.example.myapplicationnetwork;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;


public class CartActivity extends AppCompatActivity {
    private Store store;
    private RecyclerView rvCart;
    private TextView tvTotal;
    private Button btnCheckout;
    private CartAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart);
        store = (Store) getIntent().getSerializableExtra("store");

        rvCart      = findViewById(R.id.rvCart);
        tvTotal     = findViewById(R.id.tvTotal);
        btnCheckout = findViewById(R.id.btnCheckout);

        List<CartItem> items = Cart.getInstance().getItems();
        adapter = new CartAdapter(items, this);
        rvCart.setLayoutManager(new LinearLayoutManager(this));
        rvCart.setAdapter(adapter);

        updateTotal();

        btnCheckout.setOnClickListener(v -> checkout());
    }

    public void updateTotal() {
        double total = 0;
        for (CartItem ci : Cart.getInstance().getItems()) {
            total += ci.getQuantity() * ci.getProduct().getPrice();
        }
        tvTotal.setText(String.format("Σύνολο: %.2f€", total));
    }
    private void sendRatingToServer(String rateRequest) {
        new Thread(() -> {
            try (
                    Socket socket = new Socket("10.0.2.2", 4321);
                    ObjectOutputStream rateOut = new ObjectOutputStream(socket.getOutputStream());
                    ObjectInputStream rateIn  = new ObjectInputStream(socket.getInputStream())
            ) {
                rateOut.writeObject(rateRequest);
                rateOut.flush();

                Object response = rateIn.readObject();

                runOnUiThread(() ->
                        Toast.makeText(this,
                                "Server απάντησε: " + response,
                                Toast.LENGTH_SHORT
                        ).show()
                );
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this,
                                "Σφάλμα αποστολής βαθμολογίας",
                                Toast.LENGTH_LONG
                        ).show()
                );
            }
        }).start();
    }


    private void checkout() {

        List<String> orders = new ArrayList<>();
        for (CartItem ci : Cart.getInstance().getItems()) {
            String cmd = "BUY:storeName=" + store.getStoreName()
                    + ";product="   + ci.getProduct().getProductName()
                    + ";quantity="  + ci.getQuantity();
            orders.add(cmd);
        }


        new Thread(() -> {
            for (String buyRequest : orders) {
                try (
                        Socket buySocket = new Socket("10.0.2.2", 4321);
                        ObjectOutputStream buyOut =
                                new ObjectOutputStream(buySocket.getOutputStream());
                        ObjectInputStream buyIn =
                                new ObjectInputStream(buySocket.getInputStream())
                ) {

                    buyOut.writeObject(buyRequest);
                    buyOut.flush();


                    Object buyResponse = buyIn.readObject();
                    Log.d("CartActivity", "Απάντηση αγοράς: " + buyResponse);


                    runOnUiThread(() ->
                            Toast.makeText(this,
                                    "Απάντηση: " + buyResponse,
                                    Toast.LENGTH_SHORT
                            ).show()
                    );

                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();

                    runOnUiThread(() ->
                            Toast.makeText(this,
                                    "Σφάλμα κατά την αγορά: " + e.getMessage(),
                                    Toast.LENGTH_LONG
                            ).show()
                    );
                }
            }


            runOnUiThread(() -> {
                Cart.getInstance().clear();
                adapter.notifyDataSetChanged();
                updateTotal();
                Toast.makeText(this,
                        "Όλες οι αγορές ολοκληρώθηκαν!",
                        Toast.LENGTH_LONG
                ).show();
                showRatingDialog();
            });
        }).start();
    }
    private void showRatingDialog() {
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.rating_dialog, null);

        RatingBar ratingBar = dialogView.findViewById(R.id.ratingBar);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("Υποβολή", (d, w) -> {
                    int stars = (int) ratingBar.getRating();
                    String rateRequest = "RATE:storeName=" + store.getStoreName() + ";rating=" + stars;
                    sendRatingToServer(rateRequest);
                    Toast.makeText(this,
                            "Ευχαριστούμε για την βαθμολογία: " + stars + "★",
                            Toast.LENGTH_LONG
                    ).show();

                    Intent intent = new Intent(this, ClientActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);

                    finish();
                })
                .setNegativeButton("Άκυρο", (d, w) -> {

                    Intent intent = new Intent(this, ClientActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                })
                .create();

        dialog.show();


        Button btnPositive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button btnNegative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);


        btnPositive.setTextColor(Color.parseColor("#5D4037"));


        btnNegative.setTextColor(Color.parseColor("#777777"));


        dialog.setOnDismissListener(d -> {
            Intent intent = new Intent(this, ClientActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        dialog.show();
    }




}
