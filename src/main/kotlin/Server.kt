package com.ame

import com.ame.prompts.helloPrompt
import com.ame.resources.helloResource
import com.ame.tools.helloTool
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions

fun createServer(): Server {
    val server = Server(
        serverInfo = Implementation(
            name = "mcp-kotlin-template",
            version = "1.0,0"
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                resources = ServerCapabilities.Resources(
                    subscribe = true,
                    listChanged = true
                ),
                tools = ServerCapabilities.Tools(
                    listChanged = true
                ),
                prompts = ServerCapabilities.Prompts(
                    listChanged = true
                )
            )
        )
    )
    server.addResources(listOf(helloResource))
    server.addTools(listOf(helloTool))
    server.addPrompts(listOf(helloPrompt))
    return server
}