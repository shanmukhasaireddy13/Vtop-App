package tk.therealsuji.vtopchennai.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.fragments.MealsLaundryFragment;

public class MealsLaundryPagerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_MEALS = 0;
    private static final int TYPE_LAUNDRY = 1;
    
    private final MealsLaundryFragment parentFragment;

    public MealsLaundryPagerAdapter(MealsLaundryFragment parentFragment) {
        this.parentFragment = parentFragment;
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? TYPE_MEALS : TYPE_LAUNDRY;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_MEALS) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_meals_page, parent, false);
            return new MealsViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_laundry_page, parent, false);
            return new LaundryViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof MealsViewHolder) {
            parentFragment.setupMealsView(holder.itemView);
        } else if (holder instanceof LaundryViewHolder) {
            parentFragment.setupLaundryView(holder.itemView);
        }
    }

    @Override
    public int getItemCount() {
        return 2;
    }

    static class MealsViewHolder extends RecyclerView.ViewHolder {
        public MealsViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    static class LaundryViewHolder extends RecyclerView.ViewHolder {
        public LaundryViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
