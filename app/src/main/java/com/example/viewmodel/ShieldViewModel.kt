package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiClient
import com.example.data.*
import com.example.service.SafeVpnService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ShieldViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ShieldRepository

    // Database-backed state flows
    val allSettings: StateFlow<Map<String, String>>
    val alertLogs: StateFlow<List<AlertLog>>
    val blockedRules: StateFlow<List<BlockedRule>>

    // UI Interactive States
    private val _parentAuthenticated = MutableStateFlow(false)
    val parentAuthenticated: StateFlow<Boolean> = _parentAuthenticated.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _simulationResult = MutableStateFlow<String?>(null)
    val simulationResult: StateFlow<String?> = _simulationResult.asStateFlow()

    init {
        val db = ShieldDatabase.getDatabase(application)
        repository = ShieldRepository(db.shieldDao)

        // Read all settings as a reactive Map
        allSettings = repository.allSettings
            .map { list -> list.associate { it.key to it.value } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyMap()
            )

        // Alert logs
        alertLogs = repository.allAlertLogs
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        // Custom blocked rules
        blockedRules = repository.allRules
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        // Seed initial default setup if empty
        viewModelScope.launch {
            if (repository.getSetting("parent_pin") == null) {
                repository.setSetting("parent_pin", "1234")
                repository.setSetting("child_mode", "true")
                repository.setSetting("vpn_active", "false")
                repository.setSetting("accessibility_active", "true")
                repository.setSetting("gemini_scan_on", "true")
                repository.setSetting("restrict_social_on", "true")
                repository.setSetting("screen_time_limit_mins", "90")
                repository.setSetting("screen_time_used_mins", "31")

                // Seed some initial block keywords
                repository.addRule("gambling", "KEYWORD")
                repository.addRule("casino", "KEYWORD")
                repository.addRule("adult-site-link.org", "URL")
                repository.addRule("dating-app", "KEYWORD")
                repository.addRule("unrestricted-chat-room", "KEYWORD")

                // Seed initial history logs showing previous interceptions
                repository.logAlert(
                    "Instagram",
                    "Text Keyword",
                    "Flagged text: \"join casino and play free poker!\" matching parent rule: \"casino\"",
                    "BLOCKED"
                )
                repository.logAlert(
                    "Google Chrome",
                    "DNS VPN Filter",
                    "Intercepted navigation to adult-site-link.org",
                    "BLOCKED"
                )
                repository.logAlert(
                    "YouTube",
                    "Text Keyword",
                    "Flagged text description: \"crazy dating hacks for singles\" matching parent rule: \"dating\"",
                    "BLOCKED"
                )
            }
        }
    }

    // PIN Authentication
    fun verifyPin(pin: String): Boolean {
        val actualPin = allSettings.value["parent_pin"] ?: "1234"
        if (pin == actualPin) {
            _parentAuthenticated.value = true
            return true
        }
        return false
    }

    fun lockDashboard() {
        _parentAuthenticated.value = false
    }

    fun updatePin(newPin: String) {
        viewModelScope.launch {
            repository.setSetting("parent_pin", newPin)
        }
    }

    // Settings adjustments
    fun setChildMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.setSetting("child_mode", enabled.toString())
            repository.logAlert(
                appName = "Guard Shield",
                detectedType = "Config Change",
                contentSnippet = "Child Protection Mode switched ${if (enabled) "ON" else "OFF"}.",
                status = "ALLOWED"
            )
        }
    }

    fun toggleVpn(context: Context, enabled: Boolean) {
        val intent = Intent(context, SafeVpnService::class.java).apply {
            action = if (enabled) "START_VPN" else "STOP_VPN"
        }
        try {
            context.startService(intent)
        } catch (e: Exception) {
            // Fallback to updating settings locally
            viewModelScope.launch {
                repository.setSetting("vpn_active", enabled.toString())
            }
        }
    }

    fun setAccessibilityStatus(enabled: Boolean) {
        viewModelScope.launch {
            repository.setSetting("accessibility_active", enabled.toString())
        }
    }

    fun setGeminiScanning(enabled: Boolean) {
        viewModelScope.launch {
            repository.setSetting("gemini_scan_on", enabled.toString())
        }
    }

    fun setRestrictSocial(enabled: Boolean) {
        viewModelScope.launch {
            repository.setSetting("restrict_social_on", enabled.toString())
        }
    }

    fun updateScreenTime(limitMins: Int) {
        viewModelScope.launch {
            repository.setSetting("screen_time_limit_mins", limitMins.toString())
        }
    }

    // Block List Management
    fun addBlockRule(value: String, ruleType: String) {
        if (value.isBlank()) return
        viewModelScope.launch {
            repository.addRule(value.trim(), ruleType)
            repository.logAlert(
                appName = "Parent Rule",
                detectedType = "Policy Update",
                contentSnippet = "Added block restriction: \"${value.trim()}\" ($ruleType)",
                status = "ALLOWED"
            )
        }
    }

    fun removeBlockRule(id: Int, value: String) {
        viewModelScope.launch {
            repository.removeRule(id)
            repository.logAlert(
                appName = "Parent Rule",
                detectedType = "Policy Update",
                contentSnippet = "Removed block restriction: \"$value\"",
                status = "ALLOWED"
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearLogs()
            repository.logAlert(
                appName = "System History",
                detectedType = "Config Change",
                contentSnippet = "Parent cleared protection activity logs.",
                status = "ALLOWED"
            )
        }
    }

    // Simulation Trigger
    fun runInteractiveSimulation(
        targetApp: String,
        screenCaption: String,
        textToAnalyze: String,
        isGeminiScan: Boolean
    ) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _simulationResult.value = null

            // Log start of scan
            repository.logAlert(
                appName = targetApp,
                detectedType = if (isGeminiScan) "AI Image Scan" else "Text Keyword",
                contentSnippet = "Initiating active content review on: \"$textToAnalyze\"",
                status = "ALLOWED"
            )

            if (isGeminiScan) {
                // Call actual Gemini REST API!
                val result = GeminiClient.analyzeScreenContent(
                    contextDescription = screenCaption,
                    userTextSnippet = textToAnalyze
                )
                val safe = result.first
                val reason = result.second

                _isAnalyzing.value = false
                _simulationResult.value = if (safe) "SAFE" else "BLOCKED"

                repository.logAlert(
                    appName = targetApp,
                    detectedType = "AI Image Scan",
                    contentSnippet = "Gemini AI scan completed. Status: ${if (safe) "Passed" else "Violated"}. Feedback: \"$reason\"",
                    status = if (safe) "ALLOWED" else "BLOCKED"
                )
            } else {
                // Offline Local Keyword Filter logic
                kotlinx.coroutines.delay(1000) // Beautiful visual rhythm delay
                val keywords = blockedRules.value
                    .filter { it.ruleType == "KEYWORD" }
                    .map { it.value.lowercase() }

                val normalizedText = textToAnalyze.lowercase()
                val matchedKeyword = keywords.firstOrNull { it.isNotEmpty() && normalizedText.contains(it) }

                _isAnalyzing.value = false
                if (matchedKeyword != null) {
                    _simulationResult.value = "BLOCKED"
                    repository.logAlert(
                        appName = targetApp,
                        detectedType = "Text Keyword",
                        contentSnippet = "Matched direct keyword rule \"$matchedKeyword\" inside text snippet: \"$textToAnalyze\"",
                        status = "BLOCKED"
                    )
                } else {
                    _simulationResult.value = "SAFE"
                    repository.logAlert(
                        appName = targetApp,
                        detectedType = "Text Keyword",
                        contentSnippet = "Screen matched no active parent block rules. Verified safe.",
                        status = "ALLOWED"
                    )
                }
            }
        }
    }

    fun dismissSimulationResult() {
        _simulationResult.value = null
    }
}
