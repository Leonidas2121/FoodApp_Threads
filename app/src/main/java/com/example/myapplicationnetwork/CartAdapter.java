package com.example.myapplicationnetwork;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.VH> {
    private List<CartItem> items;
    private Context context;

    public CartAdapter(List<CartItem> items, Context ctx) {
        this.items = items;
        this.context = ctx;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context)
                .inflate(R.layout.item_cart, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        CartItem ci = items.get(position);
        holder.tvName.setText(ci.getProduct().getProductName());
        holder.tvQty.setText(String.valueOf(ci.getQuantity()));
        holder.tvPrice.setText(String.format("%.2f€", ci.getProduct().getPrice() * ci.getQuantity()));

        // Αφαίρεση ενός τεμαχίου ή πλήρης αφαίρεση
        holder.btnMinus.setOnClickListener(v -> {
            if (ci.getQuantity() > 1) {
                ci.setQuantity(ci.getQuantity() - 1);
            } else {
                items.remove(position);
            }
            notifyDataSetChanged();
            ((CartActivity) context).updateTotal();
        });

        holder.btnPlus.setOnClickListener(v -> {
            ci.setQuantity(ci.getQuantity() + 1);
            notifyDataSetChanged();
            ((CartActivity) context).updateTotal();
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvQty, tvPrice;
        ImageButton btnPlus, btnMinus;

        VH(@NonNull View itemView) {
            super(itemView);
            tvName  = itemView.findViewById(R.id.tvCartName);
            tvQty   = itemView.findViewById(R.id.tvCartQty);
            tvPrice = itemView.findViewById(R.id.tvCartPrice);
            btnPlus  = itemView.findViewById(R.id.btnPlus);
            btnMinus = itemView.findViewById(R.id.btnMinus);
        }
    }
}
