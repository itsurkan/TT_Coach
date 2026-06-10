package com.ttcoachai.shared.models

/** Keypoint topology of a schema-v2 pose export. Indices 0–16 are identical in both. */
enum class Topology(val jsonName: String, val keypointCount: Int) {
    COCO17("coco17", 17),
    HALPE26("halpe26", 26);

    companion object {
        fun fromJsonName(name: String): Topology? = entries.firstOrNull { it.jsonName == name }
    }
}
