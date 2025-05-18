package com.ame

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.util.collections.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered


/**
 * Start sse-server mcp on port 3001. Defaults to stdio.
 *
 * @param args
 * - "--stdio": Runs an MCP server using standard input/output.
 * - "--sse-server-ktor <port>": Runs an SSE MCP server using Ktor plugin (default if no argument is provided).
 * - "--sse-server <port>": Runs an SSE MCP server with a plain configuration.
 */
fun main(args: Array<String>) {
    val server = createServer()
    val command = args.firstOrNull() ?: "--stdio"
    val port = args.getOrNull(1)?.toIntOrNull() ?: 3001
    when (command) {
        "--stdio" -> runMcpServerUsingStdio(server)
        "--sse-server-ktor" -> runSseMcpServerUsingKtorPlugin(server, port)
        "--sse-server" -> runSseMcpServerWithPlainConfiguration(server, port)
        else -> {
            System.err.println("Error: Invalid command '$command'. Valid commands are: --stdio, --sse-server-ktor, --sse-server")
        }
    }
}


/**
 * Run the MCP server with the given transport
 *
 * @param transport The transport to use for communication
 * @return A job that completes when the server is closed
 */
fun runMcpServer(server: Server, transport: StdioServerTransport) {
    runBlocking {
        server.connect(transport)
        val done = Job()
        server.onClose {
            done.complete()
        }
        done.join()
        println("Server closed")
    }
}

/**
 * Run the MCP server using standard input/output for communication
 */
fun runMcpServerUsingStdio(server: Server) {
    // Note: The server will handle listing prompts, tools, and resources automatically.
    // The handleListResourceTemplates will return empty as defined in the Server code.
    val transport = StdioServerTransport(
        inputStream = System.`in`.asSource().buffered(),
        outputStream = System.out.asSink().buffered()
    )

    runMcpServer(server, transport)
}

fun runSseMcpServerWithPlainConfiguration(server: Server, port: Int): Unit = runBlocking {
    val servers = ConcurrentMap<String, Server>()
    println("Starting sse server on port $port. ")
    println("Use inspector to connect to the http://localhost:$port/sse")

    embeddedServer(CIO, host = "0.0.0.0", port = port) {
        install(SSE)
        routing {
            sse("/sse") {
                val transport = SseServerTransport("/message", this)

                // For SSE, you can also add prompts/tools/resources if needed:
                // server.addTool(...), server.addPrompt(...), server.addResource(...)

                servers[transport.sessionId] = server

                server.onClose {
                    println("Server closed")
                    servers.remove(transport.sessionId)
                }

                server.connect(transport)
            }
            post("/message") {
                println("Received Message")
                val sessionId: String = call.request.queryParameters["sessionId"]!!
                val transport = servers[sessionId]?.transport as? SseServerTransport
                if (transport == null) {
                    call.respond(HttpStatusCode.NotFound, "Session not found")
                    return@post
                }

                transport.handlePostMessage(call)
            }
        }
    }.start(wait = true)
}

/**
 * Starts an SSE (Server Sent Events) MCP server using the Ktor framework and the specified port.
 *
 * The url can be accessed in the MCP inspector at [http://localhost:$port]
 *
 * @param port The port number on which the SSE MCP server will listen for client connections.
 * @return Unit This method does not return a value.
 */
fun runSseMcpServerUsingKtorPlugin(server: Server, port: Int): Unit = runBlocking {
    println("Starting sse server on port $port")
    println("Use inspector to connect to the http://localhost:$port/sse")

    embeddedServer(CIO, host = "0.0.0.0", port = port) {
        mcp {
            return@mcp server
        }
    }.start(wait = true)
}