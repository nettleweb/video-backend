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

	private final InetSocketAddress address;
	private final HTTPLogger logger;

	@Nullable
	private DatagramSocket socket;
	@Nullable
	private Process process;

	private static final class X {
		private static final String cwd;
		private static final Set<Process> proc;

		static {
			cwd = new File(".").getAbsolutePath();
			proc = new HashSet<>();

			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				for (Process p : proc) {
					if (p.isAlive())
						p.destroyForcibly();
				}
			}, "fi"));
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

		Set<Process> proc = X.proc;
		proc.removeIf(p -> !p.isAlive());
		proc.add(process);

		return process;
	}

	@TestOnly
	public PoTokenProviderImpl(HTTPLogger logger) {
		InetSocketAddress address = null;

		String script = scriptPath;
		if (script != null) {
			int port = getAvailPort();
			address = new InetSocketAddress("127.0.0.1", port);

			try {
				this.process = spawnProcess(nodejsPath, script, Integer.toString(port));
				DatagramSocket socket = new DatagramSocket();
				socket.setSendBufferSize(1024);
				socket.setReuseAddress(true);
				socket.setBroadcast(true);
				socket.setSoTimeout(8000);
				socket.connect(address);
				this.socket = socket;
			} catch (IOException e) {
				logger.error(e);
				script = null; // goto
			}
		}

		if (script == null)
			logger.warn("Warning: PoToken provider has been disabled because node executable is not found.");

		this.logger = logger;
		this.address = address;
	}

	@Override
	public PoTokenResult getWebClientPoToken(String videoId) {
		InetSocketAddress address = this.address;
		DatagramSocket socket = this.socket;
		HTTPLogger logger = this.logger;
		Process process = this.process;

		if (socket != null && process != null && address != null) {
			if (!process.isAlive()) {
				synchronized (this) {
					try {
						process = this.process = spawnProcess(nodejsPath, scriptPath, Integer.toString(address.getPort()));
					} catch (Exception e) {
						logger.error("Failed to restart poToken generator: ", e);
						return null;
					}
					X.ensureTrue(process.isAlive());
				}
			}

			final int reqId = (int) System.nanoTime();
			try {
				socket.send(new DatagramPacket(new byte[]{
						0x46, 0x55, 0x43, 0x4b, 0x21,
						(byte) (reqId & 0xff),
						(byte) ((reqId >> 8) & 0xff),
						(byte) ((reqId >> 16) & 0xff),
						(byte) ((reqId >> 24) & 0xff)
				}, 0, 9, address));
			} catch (Exception e) {
				logger.error("Failed to send poToken request to generator: ", e);
				return null;
			}

			try {
				DatagramPacket packet = new DatagramPacket(new byte[1024], 0, 1024, address);
				long expTime = System.currentTimeMillis() + 15000;
				byte[] data;

				do {
					if (System.currentTimeMillis() > expTime)
						throw new IOException("Timeout after many attempts");

					socket.receive(packet);
					data = packet.getData();
				} while (data.length < 4 || reqId != ((data[0] & 0xff) | ((data[1] & 0xff) << 8) | ((data[2] & 0xff) << 16) | ((data[3] & 0xff) << 24)));

				int length = packet.getLength();
				int sep = -1;

				for (int i = 5; i < length; i++) {
					if (data[i] == 10) {
						sep = i;
						break;
					}
				}

				if (sep < 0)
					throw new IOException("Remote returned invalid data");

				String poToken = new String(data, 5, sep - 4, StandardCharsets.US_ASCII).trim();
				String visitorData = new String(data, sep + 1, length - sep - 1, StandardCharsets.US_ASCII).trim();

				if (poToken.isEmpty() || visitorData.isEmpty())
					throw new IOException("Remote returned empty data");

				return new PoTokenResult(visitorData, poToken, null);
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