package com.afriserve.smsmanager.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.afriserve.smsmanager.R
import com.afriserve.smsmanager.billing.SubscriptionHelper
import com.afriserve.smsmanager.databinding.FragmentSettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Settings Fragment with Premium Subscription Integration
 */
@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupPremiumCard()
        setupSettingsItems()
        updateSubscriptionStatus()
    }

    private fun setupPremiumCard() {
        // Premium Card Click - Launch Subscription Activity
        binding.premiumCard.setOnClickListener {
            if (SubscriptionHelper.isPaymentPending(requireContext())) {
                updateSubscriptionStatus(true)
            } else {
                SubscriptionHelper.launch(requireContext())
            }
        }

        // Premium Button Click
        binding.btnUpgradePremium.setOnClickListener {
            if (SubscriptionHelper.isPaymentPending(requireContext())) {
                updateSubscriptionStatus(true)
            } else {
                SubscriptionHelper.launch(requireContext())
            }
        }

        binding.btnRefreshStatus.setOnClickListener {
            updateSubscriptionStatus(true)
        }
    }

    private fun setupSettingsItems() {
        // Theme Settings
        binding.themeCard.setOnClickListener {
            // TODO: Show theme picker dialog
            showThemeDialog()
        }

        // Notifications Toggle
        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            // TODO: Save notification preference
            saveNotificationPreference(isChecked)
        }

        // Default SMS App
        binding.defaultSmsCard.setOnClickListener {
            // TODO: Launch default SMS app picker
            requestDefaultSmsApp()
        }

        // Biometric Authentication
        binding.biometricCard.setOnClickListener {
            // TODO: Navigate to biometric settings
            openBiometricSettings()
        }

        // Clear Cache
        binding.clearCacheCard.setOnClickListener {
            // TODO: Clear app cache
            clearAppCache()
        }

        // About
        binding.aboutCard.setOnClickListener {
            // TODO: Show about dialog
            showAboutDialog()
        }
    }

    private fun updateSubscriptionStatus(forceRefresh: Boolean = false) {
        // First show cached status (fast)
        applySubscriptionStatus(SubscriptionHelper.getCachedStatus(requireContext()))

        // Then refresh from server
        viewLifecycleOwner.lifecycleScope.launch {
            val refreshed = SubscriptionHelper.refreshSubscriptionStatus(requireContext(), forceRefresh)
            if (isAdded) {
                applySubscriptionStatus(refreshed)
            }
        }
    }

    private fun applySubscriptionStatus(status: SubscriptionHelper.SubscriptionStatus) {
        val now = System.currentTimeMillis()
        val isActive = status.premium && (status.paidUntilMillis == null || status.paidUntilMillis > now)

        if (isActive) {
            val planLabel = formatPlanLabel(status.plan)
            binding.txtSubscriptionStatus.text = if (planLabel != null) {
                "Premium Active - $planLabel"
            } else {
                "Premium Active"
            }
            binding.btnUpgradePremium.text = "Manage"
            if (shouldShowPaidUntil(status.plan)) {
                binding.txtSubscriptionPaidUntil.visibility = View.VISIBLE
                binding.txtSubscriptionPaidUntil.text = formatPaidUntil(status.paidUntilMillis)
            } else {
                binding.txtSubscriptionPaidUntil.visibility = View.GONE
            }
            return
        }

        if (SubscriptionHelper.isPaymentPending(requireContext())) {
            binding.txtSubscriptionStatus.text = "Payment processing..."
            binding.btnUpgradePremium.text = "Check Status"
            binding.txtSubscriptionPaidUntil.visibility = View.GONE
            return
        }

        binding.txtSubscriptionStatus.text = "Unlock unlimited SMS sending"
        binding.btnUpgradePremium.text = "Upgrade Now"
        binding.txtSubscriptionPaidUntil.visibility = View.GONE
    }

    private fun shouldShowPaidUntil(plan: String?): Boolean {
        val normalized = plan?.trim()?.lowercase(Locale.getDefault())
        return normalized == "daily" || normalized == "weekly"
    }

    private fun formatPlanLabel(plan: String?): String? {
        val normalized = plan?.trim()?.lowercase(Locale.getDefault())
        return when (normalized) {
            "daily" -> "Daily"
            "weekly" -> "Weekly"
            "monthly" -> "Monthly"
            "yearly" -> "Yearly"
            else -> null
        }
    }

    private fun formatPaidUntil(paidUntilMillis: Long?): String {
        if (paidUntilMillis == null || paidUntilMillis <= 0L) {
            return "Paid until: --"
        }
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
        val date = Instant.ofEpochMilli(paidUntilMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        return "Paid until: ${formatter.format(date)}"
    }

    private fun showThemeDialog() {
        val themes = arrayOf("Light", "Dark", "System")
        val currentTheme = 2 // System default

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Choose Theme")
            .setSingleChoiceItems(themes, currentTheme) { dialog, which ->
                // TODO: Apply theme
                binding.txtCurrentTheme.text = themes[which]
                dialog.dismiss()
            }
            .show()
    }

    private fun saveNotificationPreference(enabled: Boolean) {
        requireContext().getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean("notifications_enabled", enabled)
            .apply()
    }

    private fun requestDefaultSmsApp() {
        // TODO: Implement default SMS app request
        android.widget.Toast.makeText(
            requireContext(),
            "Default SMS app feature",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    private fun openBiometricSettings() {
        // TODO: Navigate to biometric settings activity
        android.widget.Toast.makeText(
            requireContext(),
            "Biometric settings",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    private fun clearAppCache() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Clear Cache")
            .setMessage("Are you sure you want to clear the app cache?")
            .setPositiveButton("Clear") { _, _ ->
                // TODO: Clear cache
                android.widget.Toast.makeText(
                    requireContext(),
                    "Cache cleared successfully",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAboutDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("About BulkSMS")
            .setMessage("Version 1.0\n\nBulkSMS Manager\n© 2026 All rights reserved")
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Update subscription status when returning from subscription activity
        updateSubscriptionStatus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
