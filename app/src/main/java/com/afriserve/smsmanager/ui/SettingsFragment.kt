package com.afriserve.smsmanager.ui

/**
 * Compatibility wrapper to avoid duplicated Settings logic across packages.
 * Keep business logic in com.afriserve.smsmanager.SettingsFragment.
 */
@Deprecated("Use com.afriserve.smsmanager.SettingsFragment")
class SettingsFragment : com.afriserve.smsmanager.SettingsFragment()
