package sh.smalley

import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import spark.Request
import spark.Response
import spark.Spark
import spark.Spark.post
import java.io.File

val emptyIngestCallback = { frame: ByteArray -> }
var ingestCallback = emptyIngestCallback

fun main(args: Array<String>) {
    try {
        File("/sys/class/gpio/export").writeText("2")
        File("/sys/class/gpio/gpio2/direction").writeText("out")
    } catch (err: Exception) { }

    try {
        File("/sys/class/gpio/export").writeText("3")
        File("/sys/class/gpio/gpio3/direction").writeText("out")
    } catch (err: Exception) { }

    Spark.port(8081)
    post("/ingest") { request: Request, response: Response -> ingestCallback(request.bodyAsBytes()) }

    val client = OkHttpClient()
    val request = okhttp3.Request.Builder()
        .url("http://surface:8080/socket")
        .build()
    client.newWebSocket(request, object: WebSocketListener() {
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
            println("Socket failed due to ${t.javaClass.simpleName}: ${t.message}")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (text.equals("capture")) {
                ingestCallback = { frame: ByteArray ->
                    ingestCallback = emptyIngestCallback
                    webSocket.send(ByteString.of(frame, 0, frame.size))
                    println("Frame sent")
                }
                ProcessBuilder(listOf("ffmpeg", "-f", "v4l2", "-s", "1920x1080", "-input_format", "mjpeg", "-i", "/dev/video0", "-frames", "1", "-f", "singlejpeg", "-s", "1920x1080", "http://surface:8081/ingest")).start()
                println("Starting capture")
            } else if (text.equals("lock")) {
                try {
                    File("/sys/class/gpio/gpio2/value").writeText("0")
                    File("/sys/class/gpio/gpio3/value").writeText("1")
                    println("Locked")
                } catch (err: Exception) {
                    println("Failed to lock  ${err.toString()}")
                }
            } else if (text.equals("unlock")) {
                try {
                    File("/sys/class/gpio/gpio2/value").writeText("1")
                    File("/sys/class/gpio/gpio3/value").writeText("0")
                    println("Unlocked")
                } catch (err: Exception) {
                    println("Failed to unlock  ${err.toString()}")
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            println("Socket closed")
        }

        override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
            println("Socket Opened")
        }
    })

    println("Started")
}