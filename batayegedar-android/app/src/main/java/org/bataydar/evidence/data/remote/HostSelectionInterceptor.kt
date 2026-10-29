package org.bataydar.evidence.data.remote

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Dynamic OkHttp interceptor that routes network requests to the configured
 * base URL (supporting physical device USB reverse, Wi-Fi LAN IP, or emulator).
 */
class HostSelectionInterceptor(private val context: Context) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val configuredUrlStr = ServerConfig.getBaseUrl(context)
        val targetHttpUrl = configuredUrlStr.toHttpUrlOrNull()

        if (targetHttpUrl != null) {
            val oldUrl = request.url
            val newUrlBuilder = oldUrl.newBuilder()
                .scheme(targetHttpUrl.scheme)
                .host(targetHttpUrl.host)
                .port(targetHttpUrl.port)

            // Reconstruct path if the target URL has custom base segments
            val targetSegments = targetHttpUrl.pathSegments.filter { it.isNotEmpty() }
            val oldSegments = oldUrl.pathSegments.filter { it.isNotEmpty() }
            val apiIndex = oldSegments.indexOf("api")

            if (targetSegments.isNotEmpty() && apiIndex >= 0) {
                val subSegments = oldSegments.subList(apiIndex + 2, oldSegments.size)
                newUrlBuilder.encodedPath("/")
                for (seg in targetSegments) {
                    newUrlBuilder.addEncodedPathSegment(seg)
                }
                for (seg in subSegments) {
                    newUrlBuilder.addEncodedPathSegment(seg)
                }
            }

            request = request.newBuilder().url(newUrlBuilder.build()).build()
        }

        return chain.proceed(request)
    }
}

