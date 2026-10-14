@file:OptIn(kotlinx.cinterop.BetaInteropApi::class)

package com.vastufirst.shared.scan

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import kotlin.coroutines.resume

/**
 * iPhone: NSURLSession. The request's timeout is the connect and read allowances together (NSURLSession
 * has one idle timeout, not two). Nothing is logged; the body carries a photograph of somebody's home.
 */
actual fun platformHttp(): Http = Http { method, url, headers, body, connectTimeoutMs, readTimeoutMs ->
    val nsUrl = NSURL.URLWithString(url) ?: return@Http null
    suspendCancellableCoroutine { cont ->
        val request = NSMutableURLRequest.requestWithURL(nsUrl)
        request.HTTPMethod = method
        request.timeoutInterval = (connectTimeoutMs + readTimeoutMs) / 1000.0
        headers.forEach { (name, value) -> request.setValue(value, forHTTPHeaderField = name) }
        if (body != null) {
            @Suppress("CAST_NEVER_SUCCEEDS")
            request.HTTPBody = (body as NSString).dataUsingEncoding(NSUTF8StringEncoding)
        }
        val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
            val http = response as? NSHTTPURLResponse
            if (error != null || http == null) {
                if (cont.isActive) cont.resume(null)
                return@dataTaskWithRequest
            }
            val text = data?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding)?.toString() }.orEmpty()
            val reply = HttpReply(http.statusCode.toInt(), text) { name -> http.valueForHTTPHeaderField(name) }
            if (cont.isActive) cont.resume(reply)
        }
        cont.invokeOnCancellation { task.cancel() }
        task.resume()
    }
}

internal actual val readerDispatcher: CoroutineDispatcher = Dispatchers.IO
