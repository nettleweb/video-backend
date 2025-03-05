package com.nettleweb.ytproxy;

import com.nettleweb.http.*;
import org.jetbrains.annotations.*;
import org.schabi.newpipe.extractor.downloader.*;
import org.schabi.newpipe.extractor.localization.*;

import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.*;

final class DownloaderImpl extends Downloader {
	private final Proxy proxy;

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

		this.proxy = p;
	}

	@Override
	public Response get(@NotNull String url) throws IOException {
		return sendRequest(url, "GET", null, null);
	}

	@Override
	public Response get(@NotNull String url, @Nullable Localization _unused) throws IOException {
		return sendRequest(url, "GET", null, null);
	}

	@Override
	public Response get(@NotNull String url, @Nullable Map<String, List<String>> headers) throws IOException {
		return sendRequest(url, "GET", headers, null);
	}

	@Override
	public Response get(@NotNull String url, @Nullable Map<String, List<String>> headers,
	                    @Nullable Localization _unused) throws IOException {
		return sendRequest(url, "GET", headers, null);
	}

	@Override
	public Response head(@NotNull String url) throws IOException {
		return sendRequest(url, "HEAD", null, null);
	}

	@Override
	public Response head(@NotNull String url, @Nullable Map<String, List<String>> headers) throws IOException {
		return sendRequest(url, "HEAD", headers, null);
	}

	@Override
	public Response post(@NotNull String url, @Nullable Map<String, List<String>> headers,
	                     @Nullable byte[] dataToSend) throws IOException {
		return sendRequest(url, "POST", headers, dataToSend);
	}

	@Override
	public Response post(@NotNull String url, @Nullable Map<String, List<String>> headers, @Nullable byte[] dataToSend,
	                     @Nullable Localization _unused) throws IOException {
		return sendRequest(url, "POST", headers, dataToSend);
	}

	@Override
	public Response postWithContentType(@NotNull String url, @Nullable Map<String, List<String>> headers,
	                                    @Nullable byte[] dataToSend, @Nullable String contentType) throws IOException {
		if (headers != null && contentType != null) {
			headers = new HashMap<>(headers);
			headers.put("Content-Type", List.of(contentType));
		}
		return sendRequest(url, "POST", headers, dataToSend);
	}

	@Override
	public Response postWithContentType(@NotNull String url, @Nullable Map<String, List<String>> headers,
	                                    @Nullable byte[] dataToSend, @Nullable Localization _unused,
	                                    @Nullable String contentType) throws IOException {
		if (headers != null && contentType != null) {
			headers = new HashMap<>(headers);
			headers.put("Content-Type", List.of(contentType));
		}
		return sendRequest(url, "POST", headers, dataToSend);
	}

	@Override
	public Response postWithContentTypeJson(@NotNull String url, @Nullable Map<String, List<String>> headers,
	                                        @Nullable byte[] dataToSend) throws IOException {
		if (headers != null) {
			headers = new HashMap<>(headers);
			headers.put("Content-Type", List.of("application/json"));
		}
		return sendRequest(url, "POST", headers, dataToSend);
	}

	@Override
	public Response postWithContentTypeJson(String url, Map<String, List<String>> headers, byte[] dataToSend, Localization localization) throws IOException {
		if (headers != null) {
			headers = new HashMap<>(headers);
			headers.put("Content-Type", List.of("application/json"));
		}
		return sendRequest(url, "POST", headers, dataToSend);
	}

	@Override
	public Response execute(@NotNull Request request) throws IOException {
		return sendRequest(request.url(), request.httpMethod(), request.headers(), request.dataToSend());
	}

	@NotNull
	private Response sendRequest(@NotNull String url, @NotNull String method,
	                             @Nullable Map<String, List<String>> headers,
	                             @Nullable byte[] data) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection(this.proxy);
		connection.setDoInput(true);
		connection.setDoOutput(true);
		connection.setUseCaches(false);
		connection.setReadTimeout(10000);
		connection.setRequestMethod(method);
		connection.setConnectTimeout(10000);
		connection.setInstanceFollowRedirects(true);

		if (headers != null) {
			for (Map.Entry<String, List<String>> e : headers.entrySet()) {
				String key = e.getKey();
				for (String v : e.getValue())
					connection.addRequestProperty(key, v);
			}
		}

		if (data != null) {
			OutputStream out = connection.getOutputStream();
			out.write(data, 0, data.length);
			out.flush();
			out.close();
		} else
			connection.connect();

		int status = connection.getResponseCode();
		data = readBytes(status >= 400 ? connection.getErrorStream() : connection.getInputStream());

		return new Response(status, connection.getResponseMessage(), connection.getHeaderFields(), data.length > 0 ?
				new String(data, StandardCharsets.UTF_8) : null, connection.getURL().toString());
	}

	private static byte[] readBytes(InputStream stream) throws IOException {
		byte[] buffer = new byte[8192];
		byte[] outBuf = new byte[0];

		for (int i = stream.read(buffer, 0, 8192); i >= 0; i = stream.read(buffer, 0, 8192))
			outBuf = mergeBytes(outBuf, buffer, i);

		stream.close();
		return outBuf;
	}

	private static byte[] mergeBytes(byte[] one, byte[] two, int len) {
		int baseLength = one.length;
		byte[] merged = new byte[baseLength + len];

		System.arraycopy(one, 0, merged, 0, baseLength);
		System.arraycopy(two, 0, merged, baseLength, len);
		return merged;
	}
}
