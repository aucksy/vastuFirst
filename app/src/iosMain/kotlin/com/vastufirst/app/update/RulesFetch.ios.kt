@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.vastufirst.app.update

import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSURLRequestUseProtocolCachePolicy
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setValue
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait

/**
 * iPhone: one NSURLSession GET, waited for on the calling (background) thread — 8 s, 200 or nothing,
 * exactly Android's rules. Never called on the main thread (see IosApp.kt).
 */
actual fun defaultRulesFetch(url: String): String? {
    val nsUrl = NSURL.URLWithString(url) ?: return null
    val request = NSMutableURLRequest.requestWithURL(nsUrl, NSURLRequestUseProtocolCachePolicy, 8.0)
    request.setValue("application/json", forHTTPHeaderField = "Accept")
    var result: String? = null
    val done = dispatch_semaphore_create(0)
    NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
        val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt()
        if (error == null && status == 200 && data != null) {
            result = NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
        }
        dispatch_semaphore_signal(done)
    }.resume()
    dispatch_semaphore_wait(done, DISPATCH_TIME_FOREVER)
    return result
}
