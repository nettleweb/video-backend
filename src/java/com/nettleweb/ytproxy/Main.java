package com.nettleweb.ytproxy;

import com.nettleweb.http.*;
import org.schabi.newpipe.extractor.*;
import org.schabi.newpipe.extractor.downloader.*;
import org.schabi.newpipe.extractor.kiosk.*;
import org.schabi.newpipe.extractor.localization.*;

import java.util.*;
import java.util.concurrent.*;

public final class Main {
	static {
		try {
			System.loadLibrary("nettleweb");
		} catch (UnsatisfiedLinkError e) {
			// ignore
		}
		Locale.setDefault(Locale.ROOT);
		System.setSecurityManager(null);
	}

	private Main() {}

	public static void main(String[] args) {
		final HTTPLogger logger = HTTPLogger.getDefault();

		int port = 80;
		int parse = 0;
		int threads = 4;
		int backlog = 255;

		String host = "0.0.0.0";
		String proxy = null;

		for (String arg : args) {
			if (arg.length() > 1 && arg.charAt(0) == '-') {
				arg = arg.charAt(1) == '-' ? arg.substring(2) : arg.substring(1);
				switch (arg) {
					case "host":
						parse = 1;
						break;
					case "port":
						parse = 2;
						break;
					case "paddr":
						parse = 6;
						break;
					case "proxy":
						parse = 3;
						break;
					case "threads":
						parse = 4;
						break;
					case "backlog":
						parse = 5;
						break;
					case "help":
						logger.println("Usage: yt-proxy [OPTION...]\n");
						logger.println("\t--host <name>    Start the HTTP server with the specified host.");
						logger.println("\t--port <port>    Start the HTTP server with the specified port.");
						logger.println("\t--proxy <url>    Send HTTP requests through the proxy server.");
						logger.println("\t--help           Show this help message and exit");
						logger.println("\t--version        Show version information and exit.\n");
						return;
					case "version":
						logger.println("v0.1.0");
						return;
					default:
						logger.printErr("Error: Invalid option: --" + arg);
						logger.printErr("Try '--help' for more information.");
						return;
				}
			} else {
				switch (parse) {
					case 1:
						host = arg;
						parse = 0;
						break;
					case 3:
						proxy = arg;
						parse = 0;
						break;
					case 6:
						parse = 0;
						break;
					case 2:
						try {
							port = Integer.parseInt(arg, 10);
							if (port < 0 || port > 65535) {
								logger.printErr("Error: Port must be between 0 and 65535.");
								System.exit(1);
								return;
							}
						} catch (Exception e) {
							logger.printErr("Error: Invalid port value: " + arg);
							System.exit(1);
							return;
						}
						parse = 0;
						break;
					case 4:
						try {
							threads = Integer.parseInt(arg, 10);
							if (threads < 0 || threads > 1024) {
								logger.printErr("Error: Threads must be between 0 and 1024.");
								System.exit(1);
								return;
							}
						} catch (Exception e) {
							logger.println("Error: Invalid threads value: " + arg);
							System.exit(1);
							return;
						}
						parse = 0;
						break;
					case 5:
						try {
							backlog = Integer.parseInt(arg, 10);
						} catch (Exception e) {
							logger.println("Error: Invalid backlog value: " + arg);
							System.exit(1);
							return;
						}
						parse = 0;
						break;
					default:
						logger.printErr("Error: Invalid arguments.");
						logger.printErr("Try '--help' for more information.");
						System.exit(1);
						return;
				}
			}
		}

		logger.log("Proxy: " + proxy);
		logger.log("Initializing...");

		{
			Downloader downloader = new DownloaderImpl(logger, proxy);

			try {
				String ip = downloader.get("https://ipinfo.io/ip").responseBody();
				if (!ip.isEmpty())
					logger.println("  Your public IP: " + ip);
			} catch (Exception e) {
				// ignore
			}

			NewPipe.init(downloader, new Localization("en", "US"), new ContentCountry("US"));
		}

		logger.log("Starting server...");
		try {
			// prefetch resources
			KioskInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/feed/trending");
		} catch (Exception e) {
			// ignore
		}

		HTTPServer server = new HTTPServer(host, port, backlog, Executors.newFixedThreadPool(threads, DefaultThreadFactory.instance));
		server.setSocketTimeout(30000);
		server.setMaxHeaderSize(16384);
		server.setHandler(new HTTPHandlerImpl(logger));
		server.setLogger(logger);
		server.start();
	}
}
