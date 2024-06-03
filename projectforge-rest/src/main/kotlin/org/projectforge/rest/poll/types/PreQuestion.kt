package org.projectforge.rest.poll.types

import com.fasterxml.jackson.databind.ObjectMapper

class PreQuestion (
    val uid: String? = null,
    val question: String? = "",
    val pType: PreType? = PreType.Neujahrsfeier,
    var answers: MutableList<String>? = mutableListOf(""),
    var parent: String? = null,
    var isRequired: Boolean? = false,
) {
    fun toJson(): String {
        return ObjectMapper().writeValueAsString(this)
    }
}

enum class PreType {
    Neujahrsfeier, Sommerfest, Teamessen,
}