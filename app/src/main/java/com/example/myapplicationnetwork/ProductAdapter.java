package com.example.myapplicationnetwork;

import android.content.Context;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.VH> {
    private List<Product> products;
    private String storeName;
    private Context context;

    private SparseIntArray quantities = new SparseIntArray();

    public ProductAdapter(List<Product> products, String storeName, Context context) {
        this.products   = products;
        this.storeName  = storeName;
        this.context    = context;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context)
                .inflate(R.layout.product_item, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Product product = products.get(position);


        holder.tvName.setText(product.getProductName());
        holder.tvPrice.setText(String.format("%.2f€", product.getPrice()));





        int qty = quantities.get(position, 0);
        holder.tvQuantity.setText(String.valueOf(qty));

        holder.btnPlus.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;

            int current = quantities.get(pos, 0) + 1;
            quantities.put(pos, current);
            holder.tvQuantity.setText(String.valueOf(current));
        });


        holder.btnMinus.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;

            int current = quantities.get(pos, 0);
            if (current > 0) {
                current--;
                quantities.put(pos, current);
                holder.tvQuantity.setText(String.valueOf(current));
            }
        });


        holder.btnAddToCart.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;

            int selectedQty = quantities.get(pos, 0);
            if (selectedQty <= 0) {
                Toast.makeText(context, "Δώσε έγκυρη ποσότητα", Toast.LENGTH_SHORT).show();
                return;
            }

            Cart.getInstance().addProduct(product, selectedQty);
            Toast.makeText(context,
                    "Προστέθηκε στο καλάθι: “" + product.getProductName() + "” x" + selectedQty,
                    Toast.LENGTH_SHORT).show();


            quantities.put(pos, 0);
            holder.tvQuantity.setText("0");
        });
    }

    @Override
    public int getItemCount() {
        return products.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView imgProduct;
        TextView tvName, tvPrice, tvQuantity;
        Button  btnAddToCart;

        ImageButton btnPlus, btnMinus;

        VH(@NonNull View itemView) {
            super(itemView);
            imgProduct    = itemView.findViewById(R.id.imgProduct);
            tvName        = itemView.findViewById(R.id.tvName);
            tvPrice       = itemView.findViewById(R.id.tvPrice);

            btnPlus       = itemView.findViewById(R.id.btnPlus);
            tvQuantity    = itemView.findViewById(R.id.tvQuantity);
            btnMinus      = itemView.findViewById(R.id.btnMinus);

            btnAddToCart  = itemView.findViewById(R.id.btnAddToCart);
        }
    }
}
