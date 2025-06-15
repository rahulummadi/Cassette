package com.example.cassette.ui.dashboard

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.example.cassette.R

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }
}