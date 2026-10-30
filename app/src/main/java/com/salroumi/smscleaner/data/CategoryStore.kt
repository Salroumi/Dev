package com.salroumi.smscleaner.data

import android.content.Context
import com.salroumi.smscleaner.analysis.CategoryRules
import com.salroumi.smscleaner.analysis.SpendCategory

/** Saves the categories the user picked by hand, on the device only. */
class CategoryStore(context: Context) {

    private val prefs = context.getSharedPreferences("category_rules", Context.MODE_PRIVATE)

    fun load(): CategoryRules = CategoryRules(
        byMerchant = read(MERCHANTS) { it },
        byMessage = read(MESSAGES) { it.toLongOrNull() },
    )

    fun save(rules: CategoryRules) {
        prefs.edit()
            .putStringSet(MERCHANTS, rules.byMerchant.map { "${it.key}\t${it.value.name}" }.toSet())
            .putStringSet(MESSAGES, rules.byMessage.map { "${it.key}\t${it.value.name}" }.toSet())
            .apply()
    }

    private fun <K> read(name: String, key: (String) -> K?): Map<K, SpendCategory> =
        prefs.getStringSet(name, emptySet()).orEmpty().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size != 2) return@mapNotNull null
            val k = key(parts[0]) ?: return@mapNotNull null
            val category = SpendCategory.entries.firstOrNull { it.name == parts[1] } ?: return@mapNotNull null
            k to category
        }.toMap()

    private companion object {
        const val MERCHANTS = "merchants"
        const val MESSAGES = "messages"
    }
}
