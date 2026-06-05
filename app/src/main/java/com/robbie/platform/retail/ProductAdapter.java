package com.robbie.platform.retail;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ProductViewHolder> {

    public interface OnProductClickListener {
        void onProductClick(Product product);
    }

    private List<Product> products = new ArrayList<>();
    private OnProductClickListener listener;

    public void setOnProductClickListener(OnProductClickListener l) {
        this.listener = l;
    }

    public void setProducts(List<Product> newProducts) {
        List<Product> oldProducts = this.products;
        List<Product> updated = new ArrayList<>(newProducts);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return oldProducts.size(); }
            @Override public int getNewListSize() { return updated.size(); }
            @Override public boolean areItemsTheSame(int oldPos, int newPos) {
                String oldId = oldProducts.get(oldPos).getId();
                String newId = updated.get(newPos).getId();
                return oldId != null && oldId.equals(newId);
            }
            @Override public boolean areContentsTheSame(int oldPos, int newPos) {
                Product o = oldProducts.get(oldPos);
                Product n = updated.get(newPos);
                return o.getPrice() == n.getPrice()
                    && o.getDiscount() == n.getDiscount()
                    && o.isAiRecommended() == n.isAiRecommended()
                    && String.valueOf(o.getName()).equals(String.valueOf(n.getName()));
            }
        });
        this.products = updated;
        diff.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_1, parent, false);
        return new ProductViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        holder.bind(products.get(position));
    }

    @Override
    public int getItemCount() {
        return products.size();
    }

    class ProductViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvProductName;

        ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProductName = itemView.findViewById(android.R.id.text1);
        }

        void bind(Product product) {
            String displayText = product.getName();
            if (product.getDiscount() > 0) {
                displayText += " - $" + String.format("%.2f", product.getDiscountedPrice()) + 
                              " (-" + product.getDiscount() + "%)";
            } else {
                displayText += " - $" + String.format("%.2f", product.getPrice());
            }
            
            if (product.isAiRecommended()) {
                displayText = "⭐ " + displayText;
            }
            
            tvProductName.setText(displayText);

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onProductClick(product);
            });
        }
    }
}
