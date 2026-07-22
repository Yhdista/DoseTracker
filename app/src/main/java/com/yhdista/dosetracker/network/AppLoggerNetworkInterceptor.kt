package com.yhdista.dosetracker.network

import com.yhdista.dosetracker.core.AppLogger
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class AppLoggerNetworkInterceptor : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestBody = request.body

        val requestLog = StringBuilder()
        requestLog.append("--> ${request.method} ${request.url}\n")
        
        val requestHeaders = request.headers
        logHeaders(requestHeaders, requestLog)

        if (requestBody != null) {
            requestLog.append("Content-Type: ${requestBody.contentType()}\n")
            requestLog.append("Content-Length: ${requestBody.contentLength()} bytes\n")
            
            if (isPlaintext(requestBody.contentType()?.toString())) {
                val buffer = Buffer()
                requestBody.writeTo(buffer)
                val charset = requestBody.contentType()?.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8
                requestLog.append("\n${buffer.readString(charset)}")
            } else {
                requestLog.append("\n[Binary Request Body]")
            }
        }
        
        AppLogger.i("Network", requestLog.toString())

        val startNs = System.nanoTime()
        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: IOException) {
            AppLogger.e("Network", "<-- HTTP FAILED: $e")
            throw e
        }
        
        val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
        val responseBody = response.body
        val responseLog = StringBuilder()

        responseLog.append("<-- ${response.code} ${response.message} ${response.request.url} (${tookMs}ms)\n")
        
        val responseHeaders = response.headers
        logHeaders(responseHeaders, responseLog)

        val contentType = responseBody.contentType()
        responseLog.append("Content-Type: $contentType\n")
        responseLog.append("Content-Length: ${responseBody.contentLength()} bytes\n")
        
        if (isPlaintext(contentType?.toString())) {
            val source = responseBody.source()
            source.request(Long.MAX_VALUE) // Buffer the entire body.
            val buffer = source.buffer
            val charset = contentType?.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8
            if (responseBody.contentLength() != 0L) {
                responseLog.append("\n${buffer.clone().readString(charset)}")
            }
        } else {
            responseLog.append("\n[Binary Response Body]")
        }

        AppLogger.i("Network", responseLog.toString())
        return response
    }

    private fun logHeaders(headers: Headers, builder: StringBuilder) {
        for (i in 0 until headers.size) {
            builder.append("${headers.name(i)}: ${headers.value(i)}\n")
        }
    }

    private fun isPlaintext(contentType: String?): Boolean {
        if (contentType == null) return false
        val mediaType = contentType.lowercase()
        return mediaType.contains("text") || 
               mediaType.contains("json") || 
               mediaType.contains("xml") || 
               mediaType.contains("html") || 
               mediaType.contains("urlencoded")
    }
}
