package com.nettleweb.ytproxy;

import com.nettleweb.http.*;
import org.jetbrains.annotations.*;
import org.schabi.newpipe.extractor.services.youtube.*;

import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.*;

final class PoTokenProviderImpl implements PoTokenProvider {
	private static final String nodejsPath;
	private static final String scriptPath;

	private final HTTPLogger logger;
	private final int port;

	@Nullable
	private DatagramSocket socket;
	@Nullable
	private Process process;

	private static final class X {
		private static final String cwd;

		static {
			cwd = new File(".").getAbsolutePath();
		}

		private static void ensureTrue(boolean s) {
			assert s;
		}
	}

	static {
		String _nodejsPath = null;
		String _scriptPath = null;

		for (String path : System.getenv("PATH").split(File.pathSeparator)) {
			File file = new File(path, "node");
			if (file.exists() && file.canExecute()) {
				_nodejsPath = file.getAbsolutePath();
				break;
			}
			if ((file = new File(path, "node.exe")).exists() && file.canExecute()) {
				_nodejsPath = file.getAbsolutePath();
				break;
			}
		}

		if (_nodejsPath != null) {
_init:
			{
				File baseDir = new File(X.cwd, "potoken").getAbsoluteFile();
				if (!baseDir.isDirectory())
					break _init;

				File script = new File(baseDir, "potoken.js");
				if (!script.isFile())
					break _init;

				File module = new File(baseDir, "node_modules");
				if (!module.isDirectory()) {
					X.ensureTrue(module.delete());

					try {
						final int code = Runtime.getRuntime().exec(new String[]{"npm", "install"}, null, baseDir).waitFor();
						if (code != 0)
							throw new IOException("Process exited with error code: " + code);
					} catch (Exception e) {
						break _init;
					}

					X.ensureTrue(module.exists());
				}

				_scriptPath = script.getAbsolutePath();
			}
		}

		nodejsPath = _nodejsPath;
		scriptPath = _scriptPath;
	}

	private static int getAvailPort() {
		try (ServerSocket socket = new ServerSocket(0, 255, InetAddress.getLocalHost())) {
			return socket.getLocalPort();
		} catch (Exception e) {
			return (int) (1024 + Math.random() * 62976);
		}
	}

	private static Process spawnProcess(String... args) throws IOException {
		Process process = Runtime.getRuntime().exec(args, new String[]{
				"PATH=/sbin:/bin",
				"TERM=linux",
				"LANG=C.UTF-8",
				"LC_ALL=C.UTF-8"
		}, null);

		long k = process.getInputStream().skip(5); // ensure the process has started
		if (k < 5)
			throw new IOException("Failed to await process start");

		return process;
	}

	@TestOnly
	public PoTokenProviderImpl(HTTPLogger logger) {
		final int port = this.port = getAvailPort();

		String script = scriptPath;
		if (script != null) {

			try {
				this.process = spawnProcess(nodejsPath, script, Integer.toString(port));
				DatagramSocket socket = new DatagramSocket();
				socket.setSendBufferSize(1024);
				socket.setReuseAddress(true);
				socket.setBroadcast(true);
				socket.connect(new InetSocketAddress("127.0.0.1", port));
				this.socket = socket;
			} catch (IOException e) {
				logger.error(e);
				script = null;
			}
		}

		if (script == null)
			logger.warn("Warning: PoToken provider has been disabled because node executable is not found.");

		this.logger = logger;
	}

	@Override
	public PoTokenResult getWebClientPoToken(String videoId) {
		DatagramSocket socket = this.socket;
		Process process = this.process;
		final int port = this.port;

		if (socket != null && process != null) {
			if (!process.isAlive()) {
				synchronized (this) {
					try {
						process = this.process = spawnProcess(nodejsPath, scriptPath, Integer.toString(port));
						socket.connect(new InetSocketAddress("127.0.0.1", port));
					} catch (Exception e) {
						logger.error("Failed to restart poToken generator: ", e);
					}
					X.ensureTrue(process.isAlive());
				}
			}

			byte[] reqId = Long.toString(System.nanoTime(), 36).getBytes(StandardCharsets.UTF_8);
			int reqIdLen = reqId.length;

			byte[] buf = new byte[37];
			buf[0] = 0x46;
			buf[1] = 0x55;
			buf[2] = 0x43;
			buf[3] = 0x4b;
			buf[4] = 0x21;
			System.arraycopy(reqId, 0, buf, 5, reqIdLen);

			try {
				DatagramPacket packet = new DatagramPacket(buf, 0, 37, new InetSocketAddress("127.0.0.1", port));
				socket.send(packet);
				socket.receive(packet);
				byte[] data = packet.getData();

				if (Arrays.equals(data, 0, reqIdLen, reqId, 0, reqIdLen)) {
					String poToken = "";
					String visitorData;

					StringBuilder builder = new StringBuilder();
					for (int i = reqIdLen + 1; i < data.length; i++) {
						byte d = data[i];
						if (d == 0) {
							poToken = builder.toString();
							builder = new StringBuilder();
						} else builder.append((char) d);
					}
					visitorData = builder.toString();

					return new PoTokenResult(visitorData, poToken, null);
				}
			} catch (Exception e) {
				logger.error("Failed to connect to poToken generator: ", e);
			}
		}

		return null;
	}

	@Override
	public PoTokenResult getWebEmbedClientPoToken(String videoId) {
		return null;
	}

	@Override
	public PoTokenResult getAndroidClientPoToken(String videoId) {
		return null;
	}

	@Override
	public PoTokenResult getIosClientPoToken(String videoId) {
		return null;
	}
}