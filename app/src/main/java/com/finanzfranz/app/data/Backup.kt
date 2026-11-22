package com.finanzfranz.app.data

import org.json.JSONArray
import org.json.JSONObject

/** JSON backup of all data. */
object Backup {
    fun toJson(d: AppData): String {
        val root = JSONObject()
        root.put("app", "finanzfranz")
        root.put("version", 1)
        root.put("transactions", JSONArray().apply {
            d.txs.forEach {
                put(JSONObject().apply {
                    put("id", it.id); put("epochDay", it.epochDay); put("amountCents", it.amountCents)
                    put("kind", it.kind); put("source", it.source); put("categoryId", it.categoryId ?: JSONObject.NULL)
                    put("note", it.note); put("fixedCostId", it.fixedCostId ?: JSONObject.NULL); put("createdAt", it.createdAt)
                })
            }
        })
        root.put("categories", JSONArray().apply {
            d.categories.forEach {
                put(JSONObject().apply {
                    put("id", it.id); put("name", it.name); put("budgetCents", it.budgetCents ?: JSONObject.NULL)
                    put("sort", it.sort); put("system", it.system)
                })
            }
        })
        root.put("fixedCosts", JSONArray().apply {
            d.fixed.forEach {
                put(JSONObject().apply {
                    put("id", it.id); put("name", it.name); put("amountCents", it.amountCents); put("source", it.source)
                    put("firstDueEpochDay", it.firstDueEpochDay); put("intervalMonths", it.intervalMonths)
                    put("bookedCount", it.bookedCount); put("active", it.active); put("income", it.income)
                })
            }
        })
        d.settings?.let { root.put("settings", JSONObject().apply { put("setupDone", it.setupDone); put("lastReconciledMonth", it.lastReconciledMonth) }) }
        return root.toString(2)
    }

    fun fromJson(text: String): AppData {
        val root = JSONObject(text)
        require(root.optString("app") == "finanzfranz") { "Keine FinanzFranz-Sicherung" }
        fun JSONObject.longOrNull(k: String): Long? = if (isNull(k) || !has(k)) null else getLong(k)
        val txs = root.getJSONArray("transactions").let { a ->
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Tx(o.getLong("id"), o.getLong("epochDay"), o.getLong("amountCents"), o.getInt("kind"), o.getInt("source"),
                    o.longOrNull("categoryId"), o.optString("note"), o.longOrNull("fixedCostId"), o.optLong("createdAt"))
            }
        }
        val cats = root.getJSONArray("categories").let { a ->
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Category(o.getLong("id"), o.getString("name"), o.longOrNull("budgetCents"), o.optInt("sort", 50), o.optBoolean("system"))
            }
        }
        val fixed = root.getJSONArray("fixedCosts").let { a ->
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                FixedCost(o.getLong("id"), o.getString("name"), o.getLong("amountCents"), o.getInt("source"),
                    o.getLong("firstDueEpochDay"), o.getInt("intervalMonths"), o.getInt("bookedCount"), o.getBoolean("active"), o.optBoolean("income"))
            }
        }
        val s = root.optJSONObject("settings")?.let { Settings(1, it.optBoolean("setupDone", true), it.optInt("lastReconciledMonth")) }
        return AppData(txs, cats, fixed, s, true)
    }
}
