package com.ame

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import sh.ondr.koja.JsonSchema
import sh.ondr.koja.jsonSchema
import sh.ondr.koja.toJsonElement

inline fun <reified T : @JsonSchema Any> getSchema(): JsonObject =
    jsonSchema<T>().toJsonElement().jsonObject["properties"]!!.jsonObject
