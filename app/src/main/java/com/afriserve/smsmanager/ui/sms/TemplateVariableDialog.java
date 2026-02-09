package com.afriserve.smsmanager.ui.sms;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.afriserve.smsmanager.R;
import com.afriserve.smsmanager.data.csv.CsvHeaderExtractor;
import com.afriserve.smsmanager.data.csv.CsvParsingResult;
import com.afriserve.smsmanager.data.parser.TemplateVariableExtractor;

import java.util.List;

/**
 * Dialog fragment for displaying template variables and message preview
 */
public class TemplateVariableDialog extends DialogFragment {
    
    private static final String ARG_CSV_RESULT = "csv_result";
    private static final String ARG_INITIAL_TEMPLATE = "initial_template";
    
    private RecyclerView recyclerViewVariables;
    private RecyclerView recyclerViewPreview;
    private TextView tvVariableCount;
    private TextView tvPreviewTitle;
    private Button btnClose;
    private Button btnUseTemplate;
    
    private CsvParsingResult csvResult;
    private String currentTemplate;
    private TemplateVariableAdapter variableAdapter;
    private MessagePreviewAdapter previewAdapter;
    private OnTemplateSelectedListener listener;
    
    public interface OnTemplateSelectedListener {
        void onTemplateSelected(String template);
        void onVariableSelected(String variable);
    }
    
    public static TemplateVariableDialog newInstance(CsvParsingResult csvResult, String initialTemplate) {
        TemplateVariableDialog dialog = new TemplateVariableDialog();
        Bundle args = new Bundle();
        args.putParcelable(ARG_CSV_RESULT, csvResult);
        args.putString(ARG_INITIAL_TEMPLATE, initialTemplate);
        dialog.setArguments(args);
        return dialog;
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Bundle args = getArguments();
        if (args != null) {
            csvResult = args.getParcelable(ARG_CSV_RESULT);
            currentTemplate = args.getString(ARG_INITIAL_TEMPLATE, "");
        }
    }
    
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.setTitle("Available Variables");
        return dialog;
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_template_variables, container, false);
        
        initViews(view);
        setupRecyclerViews();
        loadTemplateVariables();
        generatePreview();
        
        return view;
    }
    
    private void initViews(View view) {
        recyclerViewVariables = view.findViewById(R.id.recyclerViewVariables);
        recyclerViewPreview = view.findViewById(R.id.recyclerViewPreview);
        tvVariableCount = view.findViewById(R.id.tvVariableCount);
        tvPreviewTitle = view.findViewById(R.id.tvPreviewTitle);
        btnClose = view.findViewById(R.id.btnClose);
        btnUseTemplate = view.findViewById(R.id.btnUseTemplate);
        
        btnClose.setOnClickListener(v -> dismiss());
        btnUseTemplate.setOnClickListener(v -> {
            if (listener != null && currentTemplate != null && !currentTemplate.trim().isEmpty()) {
                listener.onTemplateSelected(currentTemplate);
                dismiss();
            } else {
                Toast.makeText(getContext(), "Please enter a template message", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void setupRecyclerViews() {
        // Setup variables RecyclerView
        recyclerViewVariables.setLayoutManager(new LinearLayoutManager(getContext()));
        variableAdapter = new TemplateVariableAdapter(
            new java.util.ArrayList<>(),
            new TemplateVariableAdapter.OnVariableClickListener() {
                @Override
                public void onVariableClick(TemplateVariableExtractor.TemplateVariable variable) {
                    if (listener != null) {
                        listener.onVariableSelected(variable.variableName);
                    }
                }
            }
        );
        recyclerViewVariables.setAdapter(variableAdapter);
        
        // Setup preview RecyclerView
        recyclerViewPreview.setLayoutManager(new LinearLayoutManager(getContext()));
        previewAdapter = new MessagePreviewAdapter(new java.util.ArrayList<>());
        recyclerViewPreview.setAdapter(previewAdapter);
    }
    
    private void loadTemplateVariables() {
        if (csvResult == null) return;
        
        // Extract template variables from CSV
        CsvHeaderExtractor.TemplateExtractionResult extractionResult = 
            new com.afriserve.smsmanager.data.csv.CsvHeaderExtractor().extractTemplateVariables(csvResult);
        
        // Update UI
        tvVariableCount.setText("Available Variables: " + extractionResult.variables.size());
        
        // Update variables adapter
        variableAdapter.updateVariables(extractionResult.variables);
    }
    
    private void generatePreview() {
        if (csvResult == null || currentTemplate == null || currentTemplate.trim().isEmpty()) {
            tvPreviewTitle.setText("Message Preview (Enter a template to see preview)");
            previewAdapter.updatePreviews(new java.util.ArrayList<>());
            return;
        }
        
        // Generate message preview
        List<TemplateVariableExtractor.MessagePreview> previews = 
            new com.afriserve.smsmanager.data.csv.CsvHeaderExtractor()
                .generateMessagePreview(currentTemplate, csvResult.getRecipients(), 5);
        
        tvPreviewTitle.setText("Message Preview (First " + previews.size() + " Customers)");
        previewAdapter.updatePreviews(previews);
    }
    
    public void updateTemplate(String newTemplate) {
        this.currentTemplate = newTemplate;
        generatePreview();
    }
    
    public void setOnTemplateSelectedListener(OnTemplateSelectedListener listener) {
        this.listener = listener;
    }
    
    /**
     * Adapter for displaying message previews
     */
    private static class MessagePreviewAdapter extends RecyclerView.Adapter<MessagePreviewAdapter.ViewHolder> {
        
        private List<TemplateVariableExtractor.MessagePreview> previews = new java.util.ArrayList<>();
        
        public MessagePreviewAdapter(List<TemplateVariableExtractor.MessagePreview> previews) {
            this.previews = previews;
        }
        
        public void updatePreviews(List<TemplateVariableExtractor.MessagePreview> previews) {
            this.previews = previews;
            notifyDataSetChanged();
        }
        
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_preview, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            TemplateVariableExtractor.MessagePreview preview = previews.get(position);
            holder.bind(preview);
        }
        
        @Override
        public int getItemCount() {
            return previews.size();
        }
        
        static class ViewHolder extends RecyclerView.ViewHolder {
            private TextView txtRecipientName;
            private TextView txtPhoneNumber;
            private TextView txtMessage;
            
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                txtRecipientName = itemView.findViewById(R.id.txtRecipientName);
                txtPhoneNumber = itemView.findViewById(R.id.txtPhoneNumber);
                txtMessage = itemView.findViewById(R.id.txtMessage);
            }
            
            public void bind(TemplateVariableExtractor.MessagePreview preview) {
                txtRecipientName.setText(preview.recipientName);
                txtPhoneNumber.setText(preview.phoneNumber);
                txtMessage.setText(preview.message);
            }
        }
    }
}
