package com.example.myapplicationnetwork;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class StoreAdapter extends RecyclerView.Adapter<StoreAdapter.StoreViewHolder> {

    private final List<Store> storeList = new ArrayList<>();
    private final Context context;

    public StoreAdapter(Context context) {
        this.context = context;
    }

    public void updateData(List<Store> newList) {
        storeList.clear();
        if (newList != null) {
            storeList.addAll(newList);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public StoreViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item, parent, false);
        return new StoreViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StoreViewHolder holder, int position) {
        Store store = storeList.get(position);
        holder.storeName.setText(store.getStoreName());
        holder.storeCategory.setText(store.getFoodCategory());


        holder.ratingBar.setRating((float) store.getStars());

        String logoPath = store.getStoreLogo();
        Log.d("StoreAdapter", "onBindViewHolder: θέση=" + position + " logoPath=" + logoPath);

        if (logoPath != null && !logoPath.isEmpty()) {



                String resourceName = logoPath;
                if (resourceName.startsWith("drawable/")) {
                    resourceName = resourceName.substring("drawable/".length());
                }
                Log.d("StoreAdapter", "  raw resourceName = " + resourceName);

                int dotIndex = resourceName.lastIndexOf('.');
                if (dotIndex > 0) {
                    resourceName = resourceName.substring(0, dotIndex);
                }
                Log.d("StoreAdapter", "  resourceName χωρίς ext = " + resourceName);

                int resId = context.getResources().getIdentifier(
                        resourceName, "drawable", context.getPackageName()
                );
                Log.d("StoreAdapter", "  getIdentifier → resId = " + resId);

                if (resId != 0) {
                    holder.logoImage.setImageResource(resId);
                    Log.d("StoreAdapter", "  έβαλα drawable ID=" + resId);
                } else {
                    holder.logoImage.setImageResource(R.drawable.ic_store);
                    Log.d("StoreAdapter", "  ΔΕΝ βρέθηκε drawable \"" + resourceName + "\", έβαλα default");
                }

        } else {
            // logoPath κενό ή null
            holder.logoImage.setImageResource(R.drawable.ic_store);
            Log.d("StoreAdapter", "  logoPath κενό → default icon");
        }


        holder.buyButton.setOnClickListener(v -> {
            if (store.getProducts().isEmpty()) {
                Toast.makeText(context, "Δεν υπάρχουν προϊόντα.", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(context, ProductListActivity.class);
            intent.putExtra("store", store);
            context.startActivity(intent);

        });
    }

    @Override
    public int getItemCount() {
        return storeList.size();
    }

    static class StoreViewHolder extends RecyclerView.ViewHolder {
        TextView storeName, storeCategory;
        RatingBar ratingBar;
        ImageView logoImage;
        Button buyButton;

        public StoreViewHolder(@NonNull View itemView) {
            super(itemView);
            storeName     = itemView.findViewById(R.id.storeName);
            storeCategory = itemView.findViewById(R.id.storeCategory);
            ratingBar     = itemView.findViewById(R.id.storeRating);
            logoImage     = itemView.findViewById(R.id.storeLogo);
            buyButton     = itemView.findViewById(R.id.buyButton);
        }
    }
}
