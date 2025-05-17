package com.ame.resources

import io.modelcontextprotocol.kotlin.sdk.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.Resource
import io.modelcontextprotocol.kotlin.sdk.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredResource

val helloResource = RegisteredResource(
    resource = Resource("test:hello", "hello-resource", "A nice way to say hello", "text/plain")
) { request ->
    ReadResourceResult(
        contents = listOf(
            TextResourceContents(
                text = "Oh, my most esteemed and distinguished guest, I do hereby extend my most humble and " +
                        "elaborate greetings to your magnificent presence on this truly remarkable occasion!",
                uri = request.uri,
                mimeType = "text/plain"
            )
        )
    )
}