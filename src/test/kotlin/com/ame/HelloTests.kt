package com.ame

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat

/**
 * This test demonstrates how the MCPServerTestWrapper can be reused for different tests.
 * It tests the HelloTool, HelloResource, and HelloPrompt functionality.
 */
class HelloTests {
    private lateinit var serverWrapper: MCPServerTestWrapper

    @BeforeEach
    fun setUp() {
        serverWrapper = MCPServerTestWrapper(::createServer)
        serverWrapper.start()
    }

    @AfterEach
    fun tearDown() {
        serverWrapper.close()
    }

    @Test
    fun testHelloToolWithDifferentNames() {
        runBlocking {
            val testNames = listOf("Alice", "Bob", "Charlie")

            for (testName in testNames) {
                val arguments = buildJsonObject {
                    put("name", testName)
                }
                val callToolResponse = serverWrapper.callTool("Hello Tool", arguments)
                val result = callToolResponse["result"]?.jsonObject
                assertThat(result).isNotNull()

                // Check if the response contains the name we sent
                val content = result?.get("content") as? JsonArray
                assertThat(content).isNotNull().isNotEmpty()

                val textContent = content?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content
                assertThat(textContent).isEqualTo(testName)
                    .withFailMessage("Response should contain the name we sent: $testName")
            }
        }
    }

    @Test
    fun testGetResource() {
        runBlocking {
            val getResourceResponse = serverWrapper.getResource("test:hello")
            val result = getResourceResponse["result"]?.jsonObject
            assertThat(result).isNotNull()

            val contents = result?.get("contents") as? JsonArray
            assertThat(contents).isNotNull().isNotEmpty()

            val textContent = contents?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content
            assertThat(textContent).isNotNull()
                .contains("Oh, my most esteemed and distinguished guest")
                .withFailMessage("Resource content should contain the expected greeting text")
        }
    }

    @Test
    fun testGetPrompt() {
        runBlocking {
            val arguments = buildJsonObject {
                put("user", "John")
            }
            val getPromptResponse = serverWrapper.getPrompt("Hello prompt", arguments)
            val result = getPromptResponse["result"]?.jsonObject
            assertThat(result).isNotNull()

            val messages = result?.get("messages") as? JsonArray
            assertThat(messages).isNotNull().isNotEmpty()

            val message = messages?.get(0)?.jsonObject
            assertThat(message).isNotNull()

            val role = message?.get("role")?.jsonPrimitive?.content
            assertThat(role).isEqualTo("assistant")
                .withFailMessage("Prompt message should have assistant role")

            val content = message?.get("content")?.jsonObject?.get("text")?.jsonPrimitive?.content
            assertThat(content).isNotNull()
                .contains("Craft an elaborate greeting")
                .contains("John")
                .withFailMessage("Prompt content should contain instructions to craft a greeting for John")
        }
    }
}
