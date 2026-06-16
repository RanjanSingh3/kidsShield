package com.example.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.ShieldDatabase
import com.example.data.ShieldRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SafeAccessibilityService : AccessibilityService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var repository: ShieldRepository

    override fun onCreate() {
        super.onCreate()
        val db = ShieldDatabase.getDatabase(applicationContext)
        repository = ShieldRepository(db.shieldDao)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Extract package name to identify Instagram/YouTube etc.
        val packageName = event.packageName?.toString() ?: ""
        if (packageName.isEmpty()) return

        // We only care about social/browsing targets or our own interactive mock flows
        if (packageName.contains("instagram") || 
            packageName.contains("youtube") || 
            packageName.contains("chrome") || 
            packageName.contains("com.example") ||
            packageName.contains("com.aistudio")) {
            
            val rootNode = rootInActiveWindow ?: return
            serviceScope.launch {
                // Check if child mode is ON
                val childModeOn = repository.getSetting("child_mode") == "true"
                if (!childModeOn) return@launch

                val blockedKeywords = repository.allRules.first()
                    .filter { it.ruleType == "KEYWORD" }
                    .map { it.value.lowercase() }

                // Traverse and detect
                traverseNode(rootNode, blockedKeywords, packageName)
            }
        }
    }

    private suspend fun traverseNode(node: AccessibilityNodeInfo?, keywords: List<String>, packageName: String) {
        if (node == null) return

        val text = node.text?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""

        for (keyword in keywords) {
            if (keyword.isNotEmpty() && (text.contains(keyword) || contentDesc.contains(keyword))) {
                val cleanPackageName = when {
                    packageName.contains("instagram") -> "Instagram"
                    packageName.contains("youtube") -> "YouTube"
                    packageName.contains("chrome") -> "Google Chrome"
                    else -> "kidsShiield Simulator"
                }

                val originalText = node.text?.toString() ?: node.contentDescription?.toString() ?: keyword
                repository.logAlert(
                    appName = cleanPackageName,
                    detectedType = "Text Keyword",
                    contentSnippet = "Flagged text: \"$originalText\" matching rule: \"$keyword\"",
                    status = "BLOCKED"
                )
                break
            }
        }

        for (i in 0 until node.childCount) {
            traverseNode(node.getChild(i), keywords, packageName)
        }
    }

    override fun onInterrupt() {
        Log.d("SafeAccessibilityService", "Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
