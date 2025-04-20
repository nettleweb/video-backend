package com.nettleweb.ytproxy;

import com.nettleweb.http.*;
import okhttp3.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.net.*;
import java.util.*;

final class DefaultDNSClient implements Dns {
	public static final DefaultDNSClient instance = new DefaultDNSClient();

	private DefaultDNSClient() {
	}

	@Nullable
	private static String fetchText(@NotNull String url) {
		try {
			HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection(Proxy.NO_PROXY);
			connection.setDoInput(true);
			connection.setUseCaches(true);
			connection.setReadTimeout(5000);
			connection.setRequestMethod("GET");
			connection.setConnectTimeout(8000);
			connection.setInstanceFollowRedirects(true);

			int status = connection.getResponseCode();
			if (status >= 200 && status < 300) {
				try (InputStream stream = connection.getInputStream()) {
					return Body.from(stream).text();
				}
			}
		} catch (Exception e) {
			// ignore
		}
		return null;
	}


	@NotNull
	@Override
	public List<InetAddress> lookup(@NotNull String s) throws UnknownHostException {
		String response = fetchText("https://secure.nettleweb.com/dns?n=" + s);
		if (response == null)
			throw new UnknownHostException("Failed to connect to DNS server");

		String[] items = response.trim().split("\n");
		int length = items.length;
		if (length < 1)
			throw new UnknownHostException("Failed to resolve host");

		List<InetAddress> list = new ArrayList<>(length);
		for (String item : items) {
			int k = item.indexOf(";", 1);
			if (k >= 1)
				item = item.substring(0, k);

			String[] parts = item.split("\\.", 4);
			byte[] data = new byte[4];
			data[0] = (byte) Integer.parseInt(parts[0], 10);
			data[1] = (byte) Integer.parseInt(parts[1], 10);
			data[2] = (byte) Integer.parseInt(parts[2], 10);
			data[3] = (byte) Integer.parseInt(parts[3], 10);
			list.add(Inet4Address.getByAddress(s, data));
		}

		return list;
	}
}
