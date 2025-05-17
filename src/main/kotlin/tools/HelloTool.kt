package com.ame.tools

import com.ame.getSchema
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

import sh.ondr.koja.JsonSchema

@JsonSchema
@Serializable
data class HelloToolSchema(val name: String)

val helloTool = RegisteredTool(
    tool = Tool(
        "Hello Tool", "Says hello!", Tool.Input(
            properties = getSchema<HelloToolSchema>()
        )
    )
) { request ->
    val helloRequest: HelloToolSchema = Json.decodeFromJsonElement(request.arguments)
    CallToolResult(content = listOf(TextContent(helloRequest.name)))
}
