package com.afriserve.smsmanager.ui.conversation;

import android.os.Bundle;
import android.Manifest;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.content.SharedPreferences;
import android.text.TextWatcher;
import android.text.Editable;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.app.AlertDialog;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import com.afriserve.smsmanager.workers.ScheduledSmsWorker;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.afriserve.smsmanager.R;
import com.afriserve.smsmanager.data.entity.SmsEntity;
import com.afriserve.smsmanager.data.entity.ConversationEntity;
import com.afriserve.smsmanager.databinding.FragmentConversationBinding;
import com.afriserve.smsmanager.notifications.SmsNotificationService;
import com.google.android.material.snackbar.Snackbar;
import dagger.hilt.android.AndroidEntryPoint;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.content.ContextCompat;
import android.content.Intent;
import android.net.Uri;

@AndroidEntryPoint
public class ConversationFragment extends Fragment {
    private static final String TAG = "ConversationFragment";
    private static final int CALL_PERMISSION_REQUEST_CODE = 2001;
    private FragmentConversationBinding binding;
    private ConversationViewModel viewModel;
    private ConversationAdapter adapter;
    private LinearLayoutManager layoutManager;
    private String phoneNumber;
    private String pendingCallNumber;
    private Long threadId;
    private SmsNotificationService notificationService;
    private SharedPreferences draftPrefs;
    private TextWatcher draftWatcher;
    private ConversationEntity currentConversation;
    private java.util.List<SmsEntity> currentMessages;
    private boolean initialScrollDone;
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentConversationBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }
    
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        viewModel = new ViewModelProvider(this).get(ConversationViewModel.class);
        
        // Get phone number from arguments
        if (getArguments() != null) {
            phoneNumber = getArguments().getString("phone_number");
            if (phoneNumber == null) {
                phoneNumber = getArguments().getString("address");
            }
            if (getArguments().containsKey("thread_id")) {
                long tid = getArguments().getLong("thread_id", -1L);
                threadId = tid > 0 ? tid : null;
            }
        }

        if (phoneNumber == null && threadId != null) {
            phoneNumber = "thread:" + threadId;
        }

        if (phoneNumber == null) {
            showError("No conversation id provided");
            return;
        }
        
        setupToolbar();
        setupRecyclerView();
        setupMessageInput();
        setupDrafts();
        observeViewModel();
        
        // Initialize notification service
        notificationService = new SmsNotificationService(
            requireContext(),
            new com.afriserve.smsmanager.data.contacts.ContactResolver(requireContext())
        );
        
        // Clear notifications for this conversation
        clearNotificationsForConversation();
        
        // Load conversation
        viewModel.loadConversation(phoneNumber, threadId);
    }
    
    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> {
            requireActivity().getSupportFragmentManager().popBackStack();
        });
        binding.toolbar.inflateMenu(R.menu.conversation_menu);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_call) {
                initiateCall();
                return true;
            }
            if (item.getItemId() == R.id.action_copy_number) {
                copyConversationNumber();
                return true;
            }
            return false;
        });

        binding.toolbar.setOnLongClickListener(v -> {
            copyConversationNumber();
            return true;
        });
    }
    
    private void setupRecyclerView() {
        adapter = new ConversationAdapter(
            message -> showMessageOptions(message),
            message -> showMessageOptions(message),
            requireContext()
        );
        
        layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setStackFromEnd(true);
        binding.recyclerViewMessages.setLayoutManager(layoutManager);
        binding.recyclerViewMessages.setAdapter(adapter);
    }
    
    private void setupMessageInput() {
        binding.buttonSend.setOnClickListener(v -> {
            String message = binding.editTextMessage.getText().toString().trim();
            if (!message.isEmpty()) {
                sendMessageWithSimPolicy(message);
            }
        });

        binding.buttonSend.setOnLongClickListener(v -> {
            scheduleMessage();
            return true;
        });
    }

    private void scheduleMessage() {
        String message = binding.editTextMessage.getText().toString().trim();
        if (message.isEmpty()) {
            Toast.makeText(requireContext(), "Type a message to schedule", Toast.LENGTH_SHORT).show();
            return;
        }

        Calendar now = Calendar.getInstance();
        DatePickerDialog datePicker = new DatePickerDialog(requireContext(),
            (view, year, month, dayOfMonth) -> {
                Calendar scheduled = Calendar.getInstance();
                scheduled.set(Calendar.YEAR, year);
                scheduled.set(Calendar.MONTH, month);
                scheduled.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                TimePickerDialog timePicker = new TimePickerDialog(requireContext(),
                    (timeView, hourOfDay, minute) -> {
                        scheduled.set(Calendar.HOUR_OF_DAY, hourOfDay);
                        scheduled.set(Calendar.MINUTE, minute);
                        scheduled.set(Calendar.SECOND, 0);

                        long delayMs = scheduled.getTimeInMillis() - System.currentTimeMillis();
                        if (delayMs <= 0) {
                            Toast.makeText(requireContext(), "Choose a future time", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        Data input = new Data.Builder()
                            .putString(ScheduledSmsWorker.KEY_PHONE, phoneNumber)
                            .putString(ScheduledSmsWorker.KEY_MESSAGE, message)
                            .putLong(ScheduledSmsWorker.KEY_THREAD_ID, threadId != null ? threadId : -1L)
                            .build();

                        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ScheduledSmsWorker.class)
                            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                            .setInputData(input)
                            .build();

                        WorkManager.getInstance(requireContext()).enqueue(request);
                        binding.editTextMessage.setText("");
                        clearDraft();
                        Toast.makeText(requireContext(), "Message scheduled", Toast.LENGTH_SHORT).show();
                    },
                    now.get(Calendar.HOUR_OF_DAY),
                    now.get(Calendar.MINUTE),
                    false
                );
                timePicker.show();
            },
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH),
            now.get(Calendar.DAY_OF_MONTH)
        );
        datePicker.show();
    }

    private void sendMessageWithSimPolicy(String message) {
        if (viewModel == null || message == null || message.trim().isEmpty()) {
            return;
        }

        java.util.List<SubscriptionInfo> subscriptions = getActiveSmsSubscriptions();
        if (subscriptions == null || subscriptions.size() <= 1) {
            performSend(message, -1);
            return;
        }

        showSimPicker(subscriptions, message);
    }

    private void performSend(String message, int simSlot) {
        viewModel.sendMessage(message, simSlot);
        binding.editTextMessage.setText("");
        clearDraft();
    }

    private java.util.List<SubscriptionInfo> getActiveSmsSubscriptions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return null;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.READ_PHONE_STATE)
                    != PackageManager.PERMISSION_GRANTED) {
                    return null;
                }
            }
            SubscriptionManager subscriptionManager = requireContext().getSystemService(SubscriptionManager.class);
            if (subscriptionManager == null) {
                return null;
            }
            return subscriptionManager.getActiveSubscriptionInfoList();
        } catch (Exception e) {
            return null;
        }
    }

    private void showSimPicker(java.util.List<SubscriptionInfo> subscriptions, String message) {
        try {
            int defaultSubId = SubscriptionManager.getDefaultSmsSubscriptionId();
            SubscriptionInfo defaultInfo = null;
            if (defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                for (SubscriptionInfo info : subscriptions) {
                    if (info != null && info.getSubscriptionId() == defaultSubId) {
                        defaultInfo = info;
                        break;
                    }
                }
            }

            java.util.List<CharSequence> labels = new java.util.ArrayList<>();
            java.util.List<Integer> slots = new java.util.ArrayList<>();

            String defaultLabel = "Use default SIM";
            if (defaultInfo != null) {
                defaultLabel = "Use default (" + buildSimLabel(defaultInfo) + ")";
            }
            labels.add(defaultLabel);
            slots.add(-1);

            for (SubscriptionInfo info : subscriptions) {
                if (info == null) continue;
                labels.add(buildSimLabel(info));
                slots.add(info.getSimSlotIndex());
            }

            CharSequence[] items = labels.toArray(new CharSequence[0]);
            new AlertDialog.Builder(requireContext())
                .setTitle("Choose SIM to send")
                .setItems(items, (dialog, which) -> {
                    int simSlot = slots.get(which);
                    performSend(message, simSlot);
                })
                .setNegativeButton("Cancel", null)
                .show();
        } catch (Exception e) {
            performSend(message, -1);
        }
    }

    private String buildSimLabel(SubscriptionInfo info) {
        if (info == null) return "SIM";
        int slot = info.getSimSlotIndex();
        String displayName = info.getDisplayName() != null ? info.getDisplayName().toString() : "";
        String carrierName = info.getCarrierName() != null ? info.getCarrierName().toString() : "";
        String name = !displayName.isEmpty() ? displayName : carrierName;
        String base = "SIM " + (slot + 1);
        if (name != null && !name.isEmpty() && !name.equals(base)) {
            return base + " - " + name;
        }
        return base;
    }

    private void setupDrafts() {
        try {
            draftPrefs = requireContext().getSharedPreferences("sms_drafts", android.content.Context.MODE_PRIVATE);
            String draft = draftPrefs.getString(getDraftKey(), "");
            if (draft != null && !draft.isEmpty()) {
                binding.editTextMessage.setText(draft);
                binding.editTextMessage.setSelection(draft.length());
            }
            draftWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) { }

                @Override
                public void afterTextChanged(Editable s) {
                    saveDraft(s != null ? s.toString() : "");
                }
            };
            binding.editTextMessage.addTextChangedListener(draftWatcher);
        } catch (Exception e) {
            Log.w(TAG, "Failed to setup drafts", e);
        }
    }

    private void saveDraft(String draft) {
        if (draftPrefs == null) return;
        draftPrefs.edit().putString(getDraftKey(), draft).apply();
    }

    private void clearDraft() {
        if (draftPrefs == null) return;
        draftPrefs.edit().remove(getDraftKey()).apply();
    }

    private String getDraftKey() {
        return "draft_" + (phoneNumber != null ? phoneNumber : "unknown");
    }
    
    private void observeViewModel() {
        // Observe conversation
        viewModel.conversation.observe(getViewLifecycleOwner(), conversation -> {
            if (conversation != null) {
                currentConversation = conversation;
                updateToolbar(conversation);
            }
        });
        
        // Observe messages
        viewModel.messages.observe(getViewLifecycleOwner(), messages -> {
            if (messages != null && binding != null) {
                boolean stickToBottom = shouldStickToBottom();
                currentMessages = messages;
                adapter.submitList(messages, () -> {
                    if (binding == null || adapter.getItemCount() == 0) {
                        return;
                    }
                    if (!initialScrollDone || stickToBottom) {
                        binding.recyclerViewMessages.scrollToPosition(adapter.getItemCount() - 1);
                        initialScrollDone = true;
                    }
                });
                if (binding.textEmpty != null && binding.recyclerViewMessages != null) {
                    binding.textEmpty.setVisibility(messages.isEmpty() ? View.VISIBLE : View.GONE);
                    binding.recyclerViewMessages.setVisibility(messages.isEmpty() ? View.GONE : View.VISIBLE);
                }
            }
        });
        
        // Observe UI state
        viewModel.uiState.observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            
            switch (state) {
                case LOADING:
                    binding.progressBar.setVisibility(View.VISIBLE);
                    break;
                case SENDING:
                    binding.progressBar.setVisibility(View.VISIBLE);
                    binding.buttonSend.setEnabled(false);
                    break;
                case SUCCESS:
                    binding.progressBar.setVisibility(View.GONE);
                    binding.buttonSend.setEnabled(true);
                    if (state.getMessage() != null) {
                        showSuccess(state.getMessage());
                    }
                    break;
                case ERROR:
                    binding.progressBar.setVisibility(View.GONE);
                    binding.buttonSend.setEnabled(true);
                    if (state.getMessage() != null) {
                        showError(state.getMessage());
                    }
                    break;
            }
        });
    }
    
    private void updateToolbar(ConversationEntity conversation) {
        String title = conversation.contactName != null && !conversation.contactName.isEmpty() 
            ? conversation.contactName 
            : conversation.phoneNumber;
        binding.toolbar.setTitle(title);
        if (conversation.phoneNumber != null && !conversation.phoneNumber.trim().isEmpty()) {
            phoneNumber = conversation.phoneNumber.trim();
        }
    }

    private void initiateCall() {
        String number = resolveCallNumber();
        if (number == null) {
            Toast.makeText(requireContext(), "No phone number available", Toast.LENGTH_SHORT).show();
            return;
        }
        phoneNumber = number;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED) {
            startDirectCall(number);
        } else {
            pendingCallNumber = number;
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, CALL_PERMISSION_REQUEST_CODE);
        }
    }

    private void startDirectCall(String number) {
        try {
            Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number));
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initiate call", e);
            openDialer(number);
        }
    }

    private void openDialer(String number) {
        try {
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number));
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open dialer", e);
            Toast.makeText(requireContext(), "Unable to open dialer", Toast.LENGTH_SHORT).show();
        }
    }

    private String sanitizePhoneNumber(String number) {
        if (number == null) return null;
        String trimmed = number.trim();
        if (trimmed.startsWith("thread:")) {
            return null;
        }
        String cleaned = trimmed.replaceAll("[^0-9+]", "");
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned;
    }

    private boolean shouldStickToBottom() {
        if (layoutManager == null || adapter == null) {
            return true;
        }
        int total = adapter.getItemCount();
        if (total == 0) {
            return true;
        }
        int lastVisible = layoutManager.findLastCompletelyVisibleItemPosition();
        if (lastVisible == RecyclerView.NO_POSITION) {
            lastVisible = layoutManager.findLastVisibleItemPosition();
        }
        return lastVisible >= total - 2;
    }

    private String resolveCallNumber() {
        String number = sanitizePhoneNumber(phoneNumber);
        if (number != null) {
            return number;
        }

        if (currentConversation != null) {
            number = sanitizePhoneNumber(currentConversation.phoneNumber);
            if (number != null) {
                return number;
            }
            number = sanitizePhoneNumber(currentConversation.contactName);
            if (number != null) {
                return number;
            }
        }

        if (currentMessages != null) {
            for (SmsEntity message : currentMessages) {
                if (message == null) continue;
                number = sanitizePhoneNumber(message.phoneNumber);
                if (number != null) {
                    return number;
                }
            }
        }

        return null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CALL_PERMISSION_REQUEST_CODE) {
            String number = pendingCallNumber;
            pendingCallNumber = null;
            if (number == null) return;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startDirectCall(number);
            } else {
                openDialer(number);
            }
        }
    }

    private void showMessageOptions(SmsEntity message) {
        String[] options = {"Delete", "Mark as unread", "Copy text"};
        
        new android.app.AlertDialog.Builder(requireContext())
            .setTitle("Message Options")
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0:
                        deleteMessage(message);
                        break;
                    case 1:
                        markAsUnread(message);
                        break;
                    case 2:
                        copyText(message);
                        break;
                }
            })
            .show();
    }
    
    private void deleteMessage(SmsEntity message) {
        new android.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Message")
            .setMessage("Are you sure you want to delete this message?")
            .setPositiveButton("Delete", (dialog, which) -> viewModel.deleteMessage(message))
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void markAsUnread(SmsEntity message) {
        viewModel.markAsUnread(message);
    }
    
    private void copyText(SmsEntity message) {
        android.content.ClipboardManager clipboard = 
            (android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("SMS", message.message);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(requireContext(), "Message copied", Toast.LENGTH_SHORT).show();
    }

    private void copyConversationNumber() {
        String number = resolveCopyNumber();
        if (number == null) {
            Toast.makeText(requireContext(), "No phone number available", Toast.LENGTH_SHORT).show();
            return;
        }
        android.content.ClipboardManager clipboard =
            (android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("Phone number", number);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(requireContext(), "Number copied", Toast.LENGTH_SHORT).show();
    }

    private String resolveCopyNumber() {
        String number = sanitizePhoneNumber(phoneNumber);
        if (number != null) {
            return number;
        }

        if (currentConversation != null) {
            number = sanitizePhoneNumber(currentConversation.phoneNumber);
            if (number != null) {
                return number;
            }
            number = sanitizePhoneNumber(currentConversation.contactName);
            if (number != null) {
                return number;
            }
        }

        if (currentMessages != null) {
            for (SmsEntity message : currentMessages) {
                if (message == null) continue;
                number = sanitizePhoneNumber(message.phoneNumber);
                if (number != null) {
                    return number;
                }
            }
        }

        return null;
    }
    
    private void showSuccess(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT).show();
    }
    
    private void showError(String message) {
        Snackbar.make(binding.getRoot(), "Error: " + message, Snackbar.LENGTH_LONG).show();
    }
    
    private void clearNotificationsForConversation() {
        if (notificationService != null && phoneNumber != null) {
            // Clear notification(s) for this conversation when opening it
            notificationService.cancelNotificationForConversation(phoneNumber);
            Log.d(TAG, "Cleared notifications for conversation: " + phoneNumber);
        }
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (binding != null && draftWatcher != null) {
            try {
                binding.editTextMessage.removeTextChangedListener(draftWatcher);
            } catch (Exception ignored) {
            }
        }
        binding = null;
    }
    
    // Factory method for creating instance with arguments
    public static ConversationFragment newInstance(String phoneNumber) {
        ConversationFragment fragment = new ConversationFragment();
        Bundle args = new Bundle();
        args.putString("phone_number", phoneNumber);
        fragment.setArguments(args);
        return fragment;
    }
}
