# MCP Server Template Guidelines

## Purpose
This template is designed to quickly and safely create MCP (Model Context Protocol) servers in Kotlin. It provides a structured approach to implementing resources, prompts, and tools for MCP servers.

## Documentation
KDocs are ESSENTIAL for any data class based schemas (including for all properties), as these are used to create the description that will be exposed for the MCP server.

## REST APIs
Lot's of MCP servers will be built around REST API's. Here are some general guidelines (unless overridden in the request, of course):
- Use a Ktor HTTP Client
```
suspend fun HttpClient.getForecast(latitude: Double, longitude: Double): List<String> {
    // Build the URI using provided latitude and longitude
    val uri = "/points/$latitude,$longitude"
    // Request the points data from the API
    val points = this.get(uri).body<Points>()

    // Request the forecast using the URL provided in the points response
    val forecast = this.get(points.properties.forecast).body<Forecast>()

    // Map each forecast period to a formatted string
    return forecast.properties.periods.map { period ->
        """
            ${period.name}:
            Temperature: ${period.temperature} ${period.temperatureUnit}
            Wind: ${period.windSpeed} ${period.windDirection}
            Forecast: ${period.detailedForecast}
        """.trimIndent()
    }
}
```
- Initialize the client like this, at the same time you setup the MCP Server:
```
    // Create an HTTP client with a default request configuration and JSON content negotiation
    val httpClient = HttpClient {
        defaultRequest {
            url(baseUrl)
            headers {
                append("Accept", "application/geo+json")
                append("User-Agent", "WeatherApiClient/1.0")
            }
            contentType(ContentType.Application.Json)
        }
        // Install content negotiation plugin for JSON serialization/deserialization
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }
    }
```
- Use serialization to marshal requests and unmarshal responses.
- Use data classes as the request/response
- Allow for auth keys both to be passed as an environment variable OR as a parameter to a tool.

## Testing Guidelines
Tests should be implemented at two levels, when possible:

1. **Unit Tests**: Test actual calls and functionality of individual components.
2. **End-to-End Tests**: Use the `MCPServerTestWrapper` to test the integration of components.

### Testing Rules
- Tests should NOT be added for general MCP Server functionality (like listing resources, etc.), but for the specific MCP Server being implemented.
- Tests should use AssertJ, not the standard JUnit assertions.
- When implementing the first resource/prompt/tool, remove all "Hello World" related files from the project.
- Add tests for all new functionality, ensure they run and pass. 
- Use mocks when needed for functionality that is outside the system (web servers, files, etc)
- Try to use libraries to implement functionality when available instead of writing from scratch.

### Example Test
```kotlin
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
```

## Example Boilerplate Code

### HelloTool Example
```kotlin
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
```

### HelloResource Example
```kotlin
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
```

### HelloPrompt Example
```kotlin
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
```

## Server Setup Example
```kotlin
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
```

## Changes on first run
- When implementing your first resource/prompt/tool, remember to remove all "Hello World" related files from the project. The examples above are provided as reference only.
- Rename the project and all of the instances of "mcp-kotlin-template" either as specified or make a good guess based on the request.