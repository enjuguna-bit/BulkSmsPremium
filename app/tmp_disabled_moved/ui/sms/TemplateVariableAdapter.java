package com.bulksms.smsmanager.ui.sms;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bulksms.smsmanager.R;
import com.bulksms.smsmanager.data.parser.TemplateVariableExtractor.TemplateVariable;
import java.util.List;

/**
 * Adapter for template variable buttons
 * Provides clickable variable buttons for template composition
 */
public class TemplateVariableAdapter extends RecyclerView.Adapter<TemplateVariableAdapter.VariableViewHolder> {
    
    public interface OnVariableClickListener {
        void onVariableClick(TemplateVariable variable);
    }
    
    private List<TemplateVariable> variables;
    private OnVariableClickListener listener;
    
    public TemplateVariableAdapter(List<TemplateVariable> variables, OnVariableClickListener listener) {
        this.variables = variables;
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public VariableViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_template_variable, parent, false);
        return new VariableViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull VariableViewHolder holder, int position) {
        TemplateVariable variable = variables.get(position);
        holder.bind(variable);
    }
    
    @Override
    public int getItemCount() {
        return variables != null ? variables.size() : 0;
    }
    
    public void updateVariables(List<TemplateVariable> newVariables) {
        this.variables = newVariables;
        notifyDataSetChanged();
    }
    
    class VariableViewHolder extends RecyclerView.ViewHolder {
        private Button btnVariable;
        
        public VariableViewHolder(@NonNull View itemView) {
            super(itemView);
            btnVariable = itemView.findViewById(R.id.btnVariable);
        }
        
        public void bind(TemplateVariable variable) {
            btnVariable.setText(variable.displayName);
            btnVariable.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onVariableClick(variable);
                }
            });
            
            // Set tooltip with sample value if available
            if (variable.sampleValue != null && !variable.sampleValue.isEmpty()) {
                btnVariable.setContentDescription(variable.displayName + " (Sample: " + variable.sampleValue + ")");
            } else {
                btnVariable.setContentDescription(variable.displayName);
            }
        }
    }
}
