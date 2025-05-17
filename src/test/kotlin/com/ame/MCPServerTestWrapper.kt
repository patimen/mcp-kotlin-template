package com.ame

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.*
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.*
import java.io.Closeable
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.TimeUnit

/**
 * A wrapper class for testing MCP servers using pipes for bidirectional communication.
 * This class allows for e2e testing of any MCP server implementation by sending actual requests
 * and receiving actual responses via stdio transport.
 */
class MCPServerTestWrapper(
    private val serverFactory: () -> Server
) : Closeable {
    private lateinit var server: Server
    private lateinit var testToServer: PipedOutputStream
    private lateinit var serverToTest: PipedInputStream
    private lateinit var serverToTestOutput: PipedOutputStream
    private lateinit var testFromServer: PipedInputStream
    private lateinit var serverJob: Job
    private var isStarted = false

    /**
     * Starts the server and sets up the communication channels.
     * This method must be called before sending any requests.
     */
    fun start() {
        if (isStarted) {
            return
        }

        // Create pipes for bidirectional communication
        testToServer = PipedOutputStream()
        serverToTest = PipedInputStream(testToServer)

        serverToTestOutput = PipedOutputStream()
        testFromServer = PipedInputStream(serverToTestOutput)

        server = serverFactory()

        val transport = StdioServerTransport(
            inputStream = serverToTest.asSource().buffered(),
            outputStream = serverToTestOutput.asSink().buffered()
        )

        // Start the server in a coroutine
        serverJob = GlobalScope.launch {
            runBlocking {
                server.connect(transport)
                val done = Job()
                server.onClose {
                    done.complete()
                }
                done.join()
            }
        }

        TimeUnit.MILLISECONDS.sleep(100)
        isStarted = true
    }

    /**
     * Stops the server and cleans up resources.
     * This method should be called after all tests are completed.
     */
    override fun close() {
        if (!isStarted) {
            return
        }

        runBlocking {
            server.close()
            serverJob.cancelAndJoin()
        }
        testToServer.close()
        serverToTest.close()
        serverToTestOutput.close()
        testFromServer.close()

        isStarted = false
    }

    /**
     * Sends a request to the server and returns the response.
     *
     * @param request The JSON-RPC request to send
     * @return The JSON-RPC response from the server
     */
    suspend fun sendRequest(request: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        if (!isStarted) {
            throw IllegalStateException("Server not started. Call start() before sending requests.")
        }

        val id = System.currentTimeMillis().toString()
        val requestWithId = buildJsonObject {
            request.forEach { (key, value) ->
                put(key, value)
            }
            put("id", id)
        }

        val requestString = requestWithId.toString() + "\n"
        testToServer.write(requestString.toByteArray())
        testToServer.flush()

        // Read the response
        val responseBuilder = StringBuilder()
        val buffer = ByteArray(1024)
        var bytesRead: Int

        // Wait for data to be available
        while (testFromServer.available() == 0) {
            delay(100)
        }

        // Read all available data
        while (testFromServer.available() > 0) {
            bytesRead = testFromServer.read(buffer)
            if (bytesRead > 0) {
                responseBuilder.append(String(buffer, 0, bytesRead))
            }
        }

        val responseString = responseBuilder.toString().trim()

        // Parse the response
        val responseJson = Json.parseToJsonElement(responseString).jsonObject

        responseJson
    }

    /**
     * Sends a ping request to check if the server is responding.
     *
     * @return The response from the server
     */
    suspend fun ping(): JsonObject {
        return sendRequest(buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "ping")
            put("params", buildJsonObject {})
        })
    }

    /**
     * Lists all tools available on the server.
     *
     * @return The response from the server containing the list of tools
     */
    suspend fun listTools(): JsonObject {
        return sendRequest(buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "tools/list")
            put("params", buildJsonObject {})
        })
    }

    /**
     * Calls a tool on the server.
     *
     * @param toolName The name of the tool to call
     * @param arguments The arguments to pass to the tool
     * @return The response from the server containing the tool result
     */
    suspend fun callTool(toolName: String, arguments: JsonObject): JsonObject {
        return sendRequest(buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "tools/call")
            put("params", buildJsonObject {
                put("name", toolName)
                put("arguments", arguments)
            })
        })
    }

    /**
     * Lists all resources available on the server.
     *
     * @return The response from the server containing the list of resources
     */
    suspend fun listResources(): JsonObject {
        return sendRequest(buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "resources/list")
            put("params", buildJsonObject {})
        })
    }

    /**
     * Gets a specific resource from the server.
     *
     * @param uri The URI of the resource to get
     * @return The response from the server containing the resource content
     */
    suspend fun getResource(uri: String): JsonObject {
        return sendRequest(buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "resources/read")
            put("params", buildJsonObject {
                put("uri", uri)
            })
        })
    }

    /**
     * Lists all prompts available on the server.
     *
     * @return The response from the server containing the list of prompts
     */
    suspend fun listPrompts(): JsonObject {
        return sendRequest(buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "prompts/list")
            put("params", buildJsonObject {})
        })
    }

    /**
     * Gets a specific prompt from the server.
     *
     * @param name The name of the prompt to get
     * @param arguments The arguments to pass to the prompt
     * @return The response from the server containing the prompt content
     */
    suspend fun getPrompt(name: String, arguments: JsonObject): JsonObject {
        return sendRequest(buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "prompts/get")
            put("params", buildJsonObject {
                put("name", name)
                put("arguments", arguments)
            })
        })
    }
}
