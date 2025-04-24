package com.nettleweb.ytproxy;

import com.nettleweb.http.*;
import okhttp3.*;
import okhttp3.Headers;
import org.jetbrains.annotations.*;
import org.schabi.newpipe.extractor.downloader.*;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;

import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;

final class DownloaderImpl extends Downloader {
	private final OkHttpClient client;

//	private static final class cOOkIEs implements CookieJar {
//		private final CookieStore store = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER).getCookieStore();
//
//		@NotNull
//		@Override
//		public List<Cookie> loadForRequest(@NotNull HttpUrl httpUrl) {
//			List<HttpCookie> cookies = this.store.get(httpUrl.uri());
//			List<Cookie> list = new ArrayList<>(cookies.size());
//
//			for (HttpCookie cookie : cookies) {
//				Cookie.Builder builder = new Cookie.Builder()
//						.name(cookie.getName())
//						.value(cookie.getValue())
//						.path(cookie.getPath())
//						.domain(cookie.getDomain());
//
//				{
//					long maxAge = cookie.getMaxAge();
//					if (maxAge >= 0)
//						builder.expiresAt(System.currentTimeMillis() + maxAge * 1000);
//				}
//
//				if (cookie.getSecure())
//					builder.secure();
//				if (cookie.isHttpOnly())
//					builder.httpOnly();
//
//				list.add(builder.build());
//			}
//
//			return list;
//		}
//
//		@Override
//		public void saveFromResponse(@NotNull HttpUrl httpUrl, @NotNull List<Cookie> list) {
//			CookieStore store = this.store;
//			URI uri = httpUrl.uri();
//
//			for (Cookie cookie : list) {
//				HttpCookie cookie1 = new HttpCookie(cookie.name(), cookie.value());
//				cookie1.setPath(cookie.path());
//				cookie1.setDomain(cookie.domain());
//				cookie1.setSecure(cookie.secure());
//				cookie1.setMaxAge((cookie.expiresAt() - System.currentTimeMillis()) / 1000);
//				cookie1.setHttpOnly(cookie.httpOnly());
//				store.add(uri, cookie1);
//			}
//		}
//	}

	public DownloaderImpl(@NotNull HTTPLogger logger, @Nullable String proxy) {
		Proxy p = Proxy.NO_PROXY;

		if (proxy != null && !proxy.isEmpty()) {
			try {
				URI uri = new URI(proxy);
				switch (uri.getScheme()) {
					case "http":
					case "https":
						p = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(uri.getHost(), uri.getPort()));
						break;
					case "socks4":
					case "socks5":
						p = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(uri.getHost(), uri.getPort()));
						break;
					default:
						logger.warn("Unsupported proxy protocol ignored: " + uri);
						break;
				}
			} catch (Exception e) {
				logger.warn("Failed to parse the specified proxy URL, ignoring...");
			}
		}

		this.client = new OkHttpClient.Builder()
				.followSslRedirects(true)
				.followRedirects(true)
				.connectTimeout(5000, TimeUnit.MILLISECONDS)
				.fastFallback(false)
				.callTimeout(15000, TimeUnit.MILLISECONDS)
//				.cookieJar(new cOOkIEs())
				.proxy(p)
				.dns(DefaultDNSClient.instance)
				.build();
	}

	@Override
	public Response execute(@NotNull Request request) throws IOException {
		try (okhttp3.Response r = this.createRequestCall(request.url(), request.httpMethod(),
				request.headers(), request.dataToSend()).execute(); ResponseBody b = r.body()) {
			byte[] body = b.bytes();
			return new Response(r.code(), r.message(), r.headers().toMultimap(),
					new String(body, StandardCharsets.UTF_8), body, r.request().url().toString());
		}
	}

	@Override
	public CancellableCall executeAsync(@NotNull Request request, AsyncCallback callback) throws IOException {
		Call call = this.createRequestCall(request.url(), request.httpMethod(), request.headers(), request.dataToSend());
		CancellableCall cancellableCall = new CancellableCall(call);

		call.enqueue(new Callback() {
			@Override
			public void onFailure(@NotNull Call call, @NotNull IOException e) {
				callback.onError(e);
				cancellableCall.setFinished();
			}

			@Override
			public void onResponse(@NotNull Call call, @NotNull okhttp3.Response r) {
				try (ResponseBody b = r.body()) {
					byte[] body = b.bytes();
					callback.onSuccess(new Response(r.code(), r.message(), r.headers().toMultimap(),
							new String(body, StandardCharsets.UTF_8), body, r.request().url().toString()));
				} catch (Exception e) {
					// ignore
				}

				r.close();
				cancellableCall.setFinished();
			}
		});

		return cancellableCall;
	}

	@NotNull
	private Call createRequestCall(@NotNull String url, @NotNull String method,
	                               @Nullable Map<String, List<String>> headers,
	                               @Nullable byte[] data) {
		Headers.Builder head = new Headers.Builder();
		if (headers != null) {
			for (Map.Entry<String, List<String>> e : headers.entrySet()) {
				final String k = e.getKey();
				for (String v : e.getValue())
					head.add(k, v);
			}
		}

		return this.client.newCall(new okhttp3.Request(HttpUrl.get(url), head.build(),
				method, data == null ? null : RequestBody.create(data)));
	}
}
