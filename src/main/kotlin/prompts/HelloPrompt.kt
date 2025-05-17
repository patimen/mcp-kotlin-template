package com.ame.prompts

import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredPrompt

val helloPrompt = RegisteredPrompt(
    prompt = Prompt(
        "Hello prompt",
        "Instructs the LLM to say hello to the user",
        arguments = listOf(PromptArgument("user", "the users name", true))
    )
) { request ->
    val user = request.arguments?.get("user")!!
    GetPromptResult(
        description = "Say hello to the user",
        listOf(
            PromptMessage(
                role = Role.assistant,
                TextContent(("Craft an elaborate greeting for a user named $user, and try to incorporate a pun using their name"))
            )
        )
    )

}