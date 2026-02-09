package com.afriserve.smsmanager.ui.sms;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.afriserve.smsmanager.R;
import com.afriserve.smsmanager.data.parser.TemplateVariableExtractor;

import java.util.List;

public class TemplateVariableAdapter extends ListAdapter<TemplateVariableExtractor.TemplateVariable, TemplateVariableAdapter.ViewHolder> {

    private OnVariableClickListener listener;

    public interface OnVariableClickListener {
        void onVariableClick(TemplateVariableExtractor.TemplateVariable variable);
    }

    public TemplateVariableAdapter(List<TemplateVariableExtractor.TemplateVariable> variables, OnVariableClickListener listener) {
        super(new DiffUtilCallback());
        this.listener = listener;
        submitList(variables);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_template_variable, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TemplateVariableExtractor.TemplateVariable variable = getItem(position);
        holder.bind(variable);
    }

    public void updateVariables(List<TemplateVariableExtractor.TemplateVariable> variables) {
        submitList(variables);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private com.google.android.material.button.MaterialButton btnVariable;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            btnVariable = itemView.findViewById(R.id.btnVariable);
        }

        public void bind(TemplateVariableExtractor.TemplateVariable variable) {
            btnVariable.setText(variable.variableName);
            
            btnVariable.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onVariableClick(variable);
                }
            });
        }
    }

    private static class DiffUtilCallback extends DiffUtil.ItemCallback<TemplateVariableExtractor.TemplateVariable> {
        @Override
        public boolean areItemsTheSame(@NonNull TemplateVariableExtractor.TemplateVariable oldItem, @NonNull TemplateVariableExtractor.TemplateVariable newItem) {
            return oldItem.variableName.equals(newItem.variableName);
        }

        @Override
        public boolean areContentsTheSame(@NonNull TemplateVariableExtractor.TemplateVariable oldItem, @NonNull TemplateVariableExtractor.TemplateVariable newItem) {
            return oldItem.variableName.equals(newItem.variableName) &&
                   oldItem.description.equals(newItem.description);
        }
    }
}
