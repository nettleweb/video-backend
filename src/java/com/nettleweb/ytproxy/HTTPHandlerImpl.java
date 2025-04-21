package com.nettleweb.ytproxy;

import com.nettleweb.http.*;
import org.jetbrains.annotations.*;
import org.schabi.newpipe.extractor.*;
import org.schabi.newpipe.extractor.channel.*;
import org.schabi.newpipe.extractor.kiosk.*;
import org.schabi.newpipe.extractor.linkhandler.*;
import org.schabi.newpipe.extractor.playlist.*;
import org.schabi.newpipe.extractor.search.*;
import org.schabi.newpipe.extractor.search.filter.*;
import org.schabi.newpipe.extractor.stream.*;

import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.*;

final class HTTPHandlerImpl implements HTTPHandler {
	private final HTTPLogger logger;
	private String cachedServices;

	public HTTPHandlerImpl(@NotNull HTTPLogger logger) {
		this.logger = logger;
	}

	@Override
	public HTTPResponse handleRequest(@NotNull HTTPRequest request) throws Exception {
		String method = request.method;
		switch (method) {
			case "GET":
			case "HEAD":
				break;
			case "OPTIONS":
				return new HTTPResponse(200, "", new Headers("Allow: GET, HEAD, OPTIONS"));
			default:
				return new HTTPResponse(405, "", new Headers(
						"Allow: GET, HEAD, OPTIONS",
						"Content-Type: text/plain"
				), Body.from(Res.msg405));
		}

		URI uri = new URI(request.url);
		switch (uri.getRawPath()) {
			case "/services" -> {
				String services = this.cachedServices;
				if (services == null) {
					StringBuilder str = new StringBuilder("[ ");
					for (StreamingService s: ServiceList.all()) {
						str.append("{\"id\":").append(s.getServiceId())
								.append(",\"url\":").append(encodeJSON(s.getBaseUrl()))
								.append(",\"name\":").append(encodeJSON(s.getServiceInfo().getName()));

						SearchQueryHandlerFactory factory = s.getSearchQHFactory();

						str.append(",\"sorts\":");
						encodeFilterItems(str, factory.getAvailableSortFilter());
						str.append(",\"filters\":");
						encodeFilterItems(str,factory.getAvailableContentFilter());
						str.append("},");
					}
					str.deleteCharAt(str.length() - 1).append("]");
					this.cachedServices = services = str.toString();
				}

				byte[] data = services.getBytes(StandardCharsets.UTF_8);
				return new HTTPResponse(200, "", new Headers(
						"Content-Type: text/plain",
						"Content-Length: " + data.length
				), Body.from(data));
			}
			case "/search" -> {
				Map<String, String> params = getSearchParams(uri.getRawQuery());
				String platform = params.get("t");
				String filter = params.get("f");
				String query = params.get("q");
				String page = params.get("p");
				String sort = params.get("s");

				if (query == null || query.isEmpty())
					return new HTTPResponse(400, "", new Headers("Content-Type: text/plain"), Body.from(Res.msg400));

				StreamingService service = getServiceById(platform);
				if (service == null)
					return new HTTPResponse(400, "", new Headers("Content-Type: text/plain"), Body.from(Res.msg400));

				try {
					SearchQueryHandlerFactory factory = service.getSearchQHFactory();
					SearchQueryHandler handler = factory.fromQuery(query, matchFilterItems(filter, factory.getAvailableContentFilter()),
							matchFilterItems(sort, factory.getAvailableSortFilter()));

					StringBuilder str = new StringBuilder("{");
					if (page == null || page.isEmpty()) {
						SearchInfo info = SearchInfo.getInfo(service, handler);

						// basic info
						str.append("\"id\":").append(encodeJSON(info.getId()));
						str.append(",\"url\":").append(encodeJSON(info.getUrl()));
						str.append(",\"name\":").append(encodeJSON(info.getName()));
						str.append(",\"query\":").append(encodeJSON(info.getSearchString()));
						str.append(",\"service\":").append(info.getServiceId());
						str.append(",\"corrected\":").append(info.isCorrectedSearch());
						str.append(",\"suggestion\":").append(encodeJSON(info.getSearchSuggestion()));
						str.append(",\"nextPageToken\":").append(info.hasNextPage() ?
								encodeJSON(encodePage(info.getNextPage())) : "null");

						// items
						encodeItems(str, info.getRelatedItems());
					} else {
						Page p = decodePage(page);
						if (p == null)
							return new HTTPResponse(400, "", new Headers("Content-Type: text/plain"), Body.from(Res.msg400));

						ListExtractor.InfoItemsPage<InfoItem> info = SearchInfo.getMoreItems(service, handler, p);

						// next page token
						str.append("\"nextPageToken\":").append(info.hasNextPage() ?
								encodeJSON(encodePage(info.getNextPage())) : "null");

						// items
						encodeItems(str, info.getItems());
					}

					byte[] data = str.append("}").toString().getBytes(StandardCharsets.UTF_8);
					return new HTTPResponse(200, "", new Headers(
							"Content-Type: text/plain",
							"Content-Length: " + data.length
					), Body.from(data));
				} catch (Exception e) {
					logger.error("Failed to parse search info: ", e);
					return new HTTPResponse(500, "", new Headers("Content-Type: text/plain"), Body.from(Res.msg500));
				}
			}
			case "/stream" -> {
				Map<String, String> params = getSearchParams(uri.getRawQuery());
				String svc = params.get("t");
				String url = params.get("u");

				if (url == null || url.isEmpty())
					return new HTTPResponse(400, "", new Headers("Content-Type: text/plain"), Body.from(Res.msg400));

				StreamingService service = getServiceById(svc);
				if (service == null)
					return new HTTPResponse(400, "", new Headers("Content-Type: text/plain"), Body.from(Res.msg400));

				try {
					StreamInfo info = StreamInfo.getInfo(service, url);
					StringBuilder str = new StringBuilder("{");

					// basic info
					str.append("\"id\":").append(encodeJSON(info.getId()));
					str.append(",\"url\":").append(encodeJSON(info.getUrl()));
					str.append(",\"name\":").append(encodeJSON(info.getName()));
					str.append(",\"host\":").append(encodeJSON(info.getHost()));
					str.append(",\"short\":").append(info.isShortFormContent());
					str.append(",\"service\":").append(info.getServiceId());
					str.append(",\"license\":").append(encodeJSON(info.getLicence()));
					str.append(",\"category\":").append(encodeJSON(info.getCategory()));
					str.append(",\"duration\":").append(info.getDuration());
					str.append(",\"ageLimit\":").append(info.getAgeLimit());
					str.append(",\"viewCount\":").append(info.getViewCount());
					str.append(",\"likeCount\":").append(info.getLikeCount());
					str.append(",\"thumbnail\":").append(encodeJSON(info.getThumbnailUrl()));
					str.append(",\"uploadDate\":").append(encodeJSON(info.getTextualUploadDate()));
					str.append(",\"description\":").append(encodeJSON(info.getDescription().getContent()));

					// tags
					str.append(",\"tags\":[ ");
					for (String tag : info.getTags())
						str.append(encodeJSON(tag)).append(",");
					str.deleteCharAt(str.length() - 1).append("]");

					// stream
					str.append(",\"stream\":").append(switch (info.getStreamType()) {
						case NONE ->
								"\"none\"";
						case LIVE_STREAM ->
								"\"live\"";
						case AUDIO_STREAM ->
								"\"audio\"";
						case VIDEO_STREAM ->
								"\"video\"";
						case POST_LIVE_STREAM ->
								"\"post_live\"";
						case AUDIO_LIVE_STREAM ->
								"\"audio_live\"";
						case POST_LIVE_AUDIO_STREAM ->
								"\"post_live_audio\"";
					});

					// privacy
					str.append(",\"privacy\":").append(switch (info.getPrivacy()) {
						case OTHER ->
								"\"other\"";
						case PUBLIC ->
								"\"public\"";
						case PRIVATE ->
								"\"private\"";
						case INTERNAL ->
								"\"internal\"";
						case UNLISTED ->
								"\"unlisted\"";
					});

					// uploader
					str.append(",\"uploader\":{\"url\":").append(encodeJSON(info.getUploaderUrl()))
							.append(",\"name\":").append(encodeJSON(info.getUploaderName()))
							.append(",\"avatar\":").append(encodeJSON(info.getUploaderAvatarUrl()))
							.append(",\"verified\":").append(info.isUploaderVerified())
							.append(",\"subscribers\":").append(info.getUploaderSubscriberCount())
							.append("}");

					// subchannel
					str.append(",\"subchannel\":{\"url\":").append(encodeJSON(info.getSubChannelUrl()))
							.append(",\"name\":").append(encodeJSON(info.getSubChannelName()))
							.append(",\"avatar\":").append(encodeJSON(info.getSubChannelAvatarUrl()))
							.append("}");

					// audio streams
					str.append(",\"audioStreams\":[ ");
					for (AudioStream stream : info.getAudioStreams()) {
						str.append("{\"id\":").append(encodeJSON(stream.getId()))
								.append(",\"url\":").append(encodeJSON(stream.getContent()))
								.append(",\"codec\":").append(encodeJSON(stream.getCodec()))
								.append(",\"format\":").append(stream.getFormatId())
								.append(",\"bitrate\":").append(stream.getBitrate())
								.append(",\"quality\":").append(encodeJSON(stream.getQuality()))
								.append("},");
					}
					str.deleteCharAt(str.length() - 1).append("]");

					// video streams
					str.append(",\"videoStreams\":[ ");
					for (VideoStream stream : (service == ServiceList.BiliBili || service == ServiceList.NicoNico) ? info.getVideoOnlyStreams() : info.getVideoStreams()) {
						str.append("{\"id\":").append(encodeJSON(stream.getId()))
								.append(",\"url\":").append(encodeJSON(stream.getContent()))
								.append(",\"fps\":").append(stream.getFps())
								.append(",\"codec\":").append(encodeJSON(stream.getCodec()))
								.append(",\"width\":").append(stream.getWidth())
								.append(",\"height\":").append(stream.getHeight())
								.append(",\"format\":").append(stream.getFormatId())
								.append(",\"bitrate\":").append(stream.getBitrate())
								.append(",\"quality\":").append(encodeJSON(stream.getQuality()))
								.append(",\"videoOnly\":").append(stream.isVideoOnly())
								.append(",\"resolution\":").append(encodeJSON(stream.getResolution()))
								.append("},");
					}
					str.deleteCharAt(str.length() - 1).append("]");

					// related items
					str.append(",\"relatedItems\":[ ");
					for (InfoItem item : info.getRelatedItems()) {
						if (item instanceof StreamInfoItem)
							encodeStreamInfoItem(str, (StreamInfoItem) item);
						else if (item instanceof ChannelInfoItem)
							encodeChannelInfoItem(str, (ChannelInfoItem) item);
						else if (item instanceof PlaylistInfoItem)
							encodePlaylistInfoItem(str, (PlaylistInfoItem) item);
						else
							encodeInfoItem(str, item);
					}
					str.deleteCharAt(str.length() - 1).append("]");

					byte[] data = str.append("}").toString().getBytes(StandardCharsets.UTF_8);
					return new HTTPResponse(200, "", new Headers(
							"Content-Type: text/plain",
							"Content-Length: " + data.length
					), Body.from(data));
				} catch (Exception e) {
					logger.error("Failed to parse stream info: ", e);
					return new HTTPResponse(500, "", new Headers("Content-Type: text/plain"), Body.from(Res.msg500));
				}
			}
			case "/channel" -> {
				Map<String, String> params = getSearchParams(uri.getRawQuery());
				String svc = params.get("t");
				String url = params.get("u");

				if (url == null || url.isEmpty())
					return new HTTPResponse(400, "", new Headers("Content-Type: text/plain"), Body.from(Res.msg400));

				StreamingService service = getServiceById(svc);
				if (service == null)
					return new HTTPResponse(400, "", new Headers("Content-Type: text/plain"), Body.from(Res.msg400));

				try {
					ChannelInfo info = ChannelInfo.getInfo(service, url);
					StringBuilder str = new StringBuilder("{");

					// basic info
					str.append("\"id\":").append(encodeJSON(info.getId()));
					str.append(",\"url\":").append(encodeJSON(info.getUrl()));
					str.append(",\"name\":").append(encodeJSON(info.getName()));
					str.append(",\"feed\":").append(encodeJSON(info.getFeedUrl()));
					str.append(",\"avatar\":").append(encodeJSON(info.getAvatarUrl()));
					str.append(",\"banner\":").append(encodeJSON(info.getBannerUrl()));
					str.append(",\"service\":").append(info.getServiceId());
					str.append(",\"verified\":").append(info.isVerified());
					str.append(",\"subscribers\":").append(info.getSubscriberCount());
					str.append(",\"description\":").append(encodeJSON(info.getDescription()));

					// tags
					str.append(",\"tags\":[ ");
					for (String tag : info.getTags())
						str.append(encodeJSON(tag)).append(",");
					str.deleteCharAt(str.length() - 1).append("]");

					// donation links
					str.append(",\"donationLinks\":[ ");
					for (String e : info.getDonationLinks())
						str.append(encodeJSON(e)).append(",");
					str.deleteCharAt(str.length() - 1).append("]");

					// parent channel
					str.append(",\"parentChannel\":{\"url\":").append(encodeJSON(info.getParentChannelUrl()))
							.append(",\"name\":").append(encodeJSON(info.getParentChannelName()))
							.append(",\"avatar\":").append(encodeJSON(info.getParentChannelAvatarUrl()))
							.append("}");

					byte[] data = str.append("}").toString().getBytes(StandardCharsets.UTF_8);
					return new HTTPResponse(200, "", new Headers(
							"Content-Type: text/plain",
							"Content-Length: " + data.length
					), Body.from(data));
				} catch (Exception e) {
					logger.error("Failed to parse channel info: ", e);
					return new HTTPResponse(500, "", new Headers("Content-Type: text/plain"), Body.from(Res.msg500));
				}
			}
			case "/playlist" -> {
				Map<String, String> params = getSearchParams(uri.getRawQuery());
				String svc = params.get("t");
				String url = params.get("u");
				String page = params.get("p");

				if (url == null || url.isEmpty())
					return new HTTPResponse(400, "", new Headers("Content-Type: text/plain"), Body.from(Res.msg400));

				StreamingService service = getServiceById(svc);
				if (service == null)
					return new HTTPResponse(400, "", new Headers("Content-Type: text/plain"), Body.from(Res.msg400));

				try {
					StringBuilder str = new StringBuilder("{");
					if (page == null || page.isEmpty()) {
						PlaylistInfo info = PlaylistInfo.getInfo(service, url);

						// basic info
						str.append("\"id\":").append(encodeJSON(info.getId()));
						str.append(",\"url\":").append(encodeJSON(info.getUrl()));
						str.append(",\"name\":").append(encodeJSON(info.getName()));
						str.append(",\"banner\":").append(encodeJSON(info.getBannerUrl()));
						str.append(",\"streams\":").append(info.getStreamCount());
						str.append(",\"service\":").append(info.getServiceId());
						str.append(",\"thumbnail\":").append(encodeJSON(info.getThumbnailUrl()));
						str.append(",\"nextPageToken\":").append(info.hasNextPage() ?
								encodeJSON(encodePage(info.getNextPage())) : "null");

						// playlist
						str.append(",\"playlist\":").append(switch (info.getPlaylistType()) {
							case NORMAL ->
									"\"normal\"";
							case MIX_GENRE ->
									"\"mix_genre\"";
							case MIX_MUSIC ->
									"\"mix_music\"";
							case MIX_STREAM ->
									"\"mix_stream\"";
							case MIX_CHANNEL ->
									"\"mix_channel\"";
						});

						// uploader
						str.append(",\"uploader\":{\"url\":").append(encodeJSON(info.getUploaderUrl()))
								.append(",\"name\":").append(encodeJSON(info.getUploaderName()))
								.append(",\"avatar\":").append(encodeJSON(info.getUploaderAvatarUrl()))
								.append("}");

						// subchannel
						str.append(",\"subchannel\":{\"url\":").append(encodeJSON(info.getSubChannelUrl()))
								.append(",\"name\":").append(encodeJSON(info.getSubChannelName()))
								.append(",\"avatar\":").append(encodeJSON(info.getSubChannelAvatarUrl()))
								.append("}");

						// related items
						encodeItems(str, info.getRelatedItems());
					} else {
						Page p = decodePage(page);
						if (p == null)
							return new HTTPResponse(400, "", new Headers("Content-Type: text/plain"), Body.from(Res.msg400));

						ListExtractor.InfoItemsPage<StreamInfoItem> info = PlaylistInfo.getMoreItems(service, url, p);

						// next page token
						str.append("\"nextPageToken\":").append(info.hasNextPage() ?
								encodeJSON(encodePage(info.getNextPage())) : "null");

						// items
						encodeItems(str, info.getItems());
					}

					byte[] data = str.append("}").toString().getBytes(StandardCharsets.UTF_8);
					return new HTTPResponse(200, "", new Headers(
							"Content-Type: text/plain",
							"Content-Length: " + data.length
					), Body.from(data));
				} catch (Exception e) {
					logger.error("Failed to parse playlist info: ", e);
					return new HTTPResponse(500, "", new Headers("Content-Type: text/plain"), Body.from(Res.msg500));
				}
			}
			case "/trending" -> {
				Map<String, String> params = getSearchParams(uri.getRawQuery());
				String svc = params.get("t");
				String page = params.get("p");

				StreamingService service = getServiceById(svc);
				if (service == null)
					return new HTTPResponse(400, "", new Headers("Content-Type: text/plain"), Body.from(Res.msg400));

				try {
					KioskExtractor<?> extractor = service.getKioskList().getDefaultKioskExtractor();
					StringBuilder str = new StringBuilder("{");

					extractor.fetchPage();

					if (page == null || page.isEmpty()) {
						KioskInfo info = KioskInfo.getInfo(extractor);

						// basic info
						str.append("\"id\":").append(encodeJSON(info.getId()));
						str.append(",\"url\":").append(encodeJSON(info.getUrl()));
						str.append(",\"name\":").append(encodeJSON(info.getName()));
						str.append(",\"service\":").append(info.getServiceId());
						str.append(",\"nextPageToken\":").append(info.hasNextPage() ?
								encodeJSON(encodePage(info.getNextPage())) : "null");

						// items
						encodeItems(str, info.getRelatedItems());
					} else {
						Page p = decodePage(page);
						if (p == null)
							return new HTTPResponse(400, "", new Headers("Content-Type: text/plain"), Body.from(Res.msg400));

						ListExtractor.InfoItemsPage<?> info = extractor.getPage(p);

						// next page token
						str.append("\"nextPageToken\":").append(info.hasNextPage() ?
								encodeJSON(encodePage(info.getNextPage())) : "null");

						// items
						encodeItems(str, info.getItems());
					}

					byte[] data = str.append("}").toString().getBytes(StandardCharsets.UTF_8);
					return new HTTPResponse(200, "", new Headers(
							"Content-Type: text/plain",
							"Content-Length: " + data.length
					), Body.from(data));
				} catch (Exception e) {
					logger.error("Failed to parse playlist info: ", e);
					return new HTTPResponse(500, "", new Headers("Content-Type: text/plain"), Body.from(Res.msg500));
				}
			}
			case "/robots.txt" -> {
				return new HTTPResponse(200, "", new Headers("Content-Type: text/plain"), Body.from(Res.robots_txt));
			}
			case "/favicon.ico" -> {
				return new HTTPResponse(200, "", new Headers("Content-Type: text/plain"), Body.from(Res.favicon_ico));
			}
			default -> {
				return new HTTPResponse(404, "", new Headers("Content-Type: text/plain"), Body.from(Res.msg404));
			}
		}
	}

	private static String encodeJSON(@Nullable String str) {
		if (str == null)
			return "null";

		StringBuilder builder = new StringBuilder("\"");
		for (char ch : str.toCharArray()) {
			switch (ch) {
				case '\"':
					builder.append("\\\"");
					break;
				case '\\':
					builder.append("\\\\");
					break;
				case '\b':
					builder.append("\\b");
					break;
				case '\f':
					builder.append("\\f");
					break;
				case '\n':
					builder.append("\\n");
					break;
				case '\r':
					builder.append("\\r");
					break;
				case '\t':
					builder.append("\\t");
					break;
				default:
					if (ch < 0x20) {
						String code = Integer.toString(ch, 16);
						code = switch (code.length()) {
							case 0 ->
									"0000";
							case 1 ->
									"000" + code;
							case 2 ->
									"00" + code;
							case 3 ->
									"0" + code;
							default ->
									code;
						};
						builder.append("\\u").append(code);
					} else
						builder.append(ch);
					break;
			}
		}

		return builder.append("\"").toString();
	}

	private static String encodePage(Page page) {
		try (ByteArrayOutputStream str1 = new ByteArrayOutputStream(); ObjectOutputStream str2 =
				new ObjectOutputStream(str1)) {

			str2.writeObject(page);
			str2.flush();
			return Base64.getEncoder().encodeToString(str1.toByteArray());
		} catch (Exception e) {
			throw new RuntimeException("Failed to serialize page object", e);
		}
	}

	private Page decodePage(String data) {
		try (ByteArrayInputStream str1 = new ByteArrayInputStream(Base64.getDecoder().decode(data)); ObjectInputStream str2 = new ObjectInputStream(str1)) {
			return (Page) str2.readObject();
		} catch (Exception e) {
			logger.error("Failed to decode page object: ", e);
			return null; // be more flexible while decoding
		}
	}

	private static void encodeItems(StringBuilder str, Iterable<? extends InfoItem> items) {
		str.append(",\"results\":[ ");
		for (InfoItem item : items) {
			if (item instanceof StreamInfoItem)
				encodeStreamInfoItem(str, (StreamInfoItem) item);
			else if (item instanceof ChannelInfoItem)
				encodeChannelInfoItem(str, (ChannelInfoItem) item);
			else if (item instanceof PlaylistInfoItem)
				encodePlaylistInfoItem(str, (PlaylistInfoItem) item);
			else
				encodeInfoItem(str, item);
		}
		str.deleteCharAt(str.length() - 1).append("]");
	}

	private static void encodeInfoItem(StringBuilder str, InfoItem item) {
		str.append("{\"url\":").append(encodeJSON(item.getUrl()))
				.append(",\"name\":").append(encodeJSON(item.getName()))
				.append(",\"type\":").append(switch (item.getInfoType()) {
					case STAFF ->
							"\"staff\"";
					case STREAM ->
							"\"stream\"";
					case CHANNEL ->
							"\"channel\"";
					case COMMENT ->
							"\"comment\"";
					case PLAYLIST ->
							"\"playlist\"";
					case BULLET_COMMENT ->
							"\"bullet_comment\"";
				})
				.append(",\"thumbnail\":").append(encodeJSON(item.getThumbnailUrl()))
				.append("},");
	}

	private static void encodeStreamInfoItem(StringBuilder str, StreamInfoItem item) {
		str.append("{\"url\":").append(encodeJSON(item.getUrl()))
				.append(",\"name\":").append(encodeJSON(item.getName()))
				.append(",\"type\":").append("\"stream\"")
				.append(",\"short\":").append(item.isShortFormContent())
				.append(",\"stream\":").append(switch (item.getStreamType()) {
					case NONE ->
							"\"none\"";
					case LIVE_STREAM ->
							"\"live\"";
					case AUDIO_STREAM ->
							"\"audio\"";
					case VIDEO_STREAM ->
							"\"video\"";
					case POST_LIVE_STREAM ->
							"\"post_live\"";
					case AUDIO_LIVE_STREAM ->
							"\"audio_live\"";
					case POST_LIVE_AUDIO_STREAM ->
							"\"post_live_audio\"";
				})
				.append(",\"duration\":").append(item.getDuration())
				.append(",\"viewCount\":").append(item.getViewCount())
				.append(",\"thumbnail\":").append(encodeJSON(item.getThumbnailUrl()))
				.append(",\"uploadDate\":").append(encodeJSON(item.getTextualUploadDate()))
				.append(",\"description\":").append(encodeJSON(item.getShortDescription()));

		str.append(",\"uploader\":{\"url\":").append(encodeJSON(item.getUploaderUrl()))
				.append(",\"name\":").append(encodeJSON(item.getUploaderName()))
				.append(",\"avatar\":").append(encodeJSON(item.getUploaderAvatarUrl()))
				.append(",\"verified\":").append(item.isUploaderVerified())
				.append("}},");
	}

	private static void encodeChannelInfoItem(StringBuilder str, ChannelInfoItem item) {
		str.append("{\"url\":").append(encodeJSON(item.getUrl()))
				.append(",\"name\":").append(encodeJSON(item.getName()))
				.append(",\"type\":").append("\"channel\"")
				.append(",\"streams\":").append(item.getStreamCount())
				.append(",\"verified\":").append(item.isVerified())
				.append(",\"thumbnail\":").append(encodeJSON(item.getThumbnailUrl()))
				.append(",\"subscribers\":").append(item.getSubscriberCount())
				.append(",\"description\":").append(encodeJSON(item.getDescription()))
				.append("},");
	}

	private static void encodePlaylistInfoItem(StringBuilder str, PlaylistInfoItem item) {
		str.append("{\"url\":").append(encodeJSON(item.getUrl()))
				.append(",\"name\":").append(encodeJSON(item.getName()))
				.append(",\"type\":").append("\"playlist\"")
				.append(",\"streams\":").append(item.getStreamCount())
				.append(",\"uploader\":").append(encodeJSON(item.getUploaderName()))
				.append(",\"playlist\":").append(switch (item.getPlaylistType()) {
					case NORMAL ->
							"\"normal\"";
					case MIX_GENRE ->
							"\"mix_genre\"";
					case MIX_MUSIC ->
							"\"mix_music\"";
					case MIX_STREAM ->
							"\"mix_stream\"";
					case MIX_CHANNEL ->
							"\"mix_channel\"";
				})
				.append(",\"thumbnail\":").append(encodeJSON(item.getThumbnailUrl()))
				.append("},");
	}

	private static void encodeFilterItems(StringBuilder str, @Nullable Filter filter) {
		str.append("[ ");

		if (filter != null) {
			for (FilterGroup group: filter.getFilterGroups()) {
				str.append("{\"id\":").append(group.identifier)
						.append(",\"name\":").append(encodeJSON(group.groupName))
						.append(",\"single\":").append(group.onlyOneCheckable)
						.append(",\"items\":[ ");

				for (FilterItem item: group.filterItems) {
					str.append("{\"id\":").append(item.getIdentifier())
							.append(",\"name\":").append(encodeJSON(item.getName()))
							.append("},");
				}

				str.deleteCharAt(str.length() - 1).append("]},");
			}
		}

		str.deleteCharAt(str.length() - 1).append("]");
	}

	private static StreamingService getServiceById(@Nullable String id) {
		if (id == null || id.isEmpty())
			return ServiceList.YouTube;

		try {
			return ServiceList.all().get(Integer.parseUnsignedInt(id, 10));
		} catch (Exception e) {
			return null;
		}
	}

	private static Map<String, String> getSearchParams(@Nullable String query) {
		Map<String, String> params = new HashMap<>();
		if (query == null || query.isEmpty())
			return params;

		for (String part : query.split("&")) {
			int i = part.indexOf("=", 1);
			if (i >= 0)
				params.put(URLDecoder.decode(part.substring(0, i), StandardCharsets.UTF_8),
						URLDecoder.decode(part.substring(i + 1), StandardCharsets.UTF_8));
			else
				params.put(URLDecoder.decode(part, StandardCharsets.UTF_8), "");
		}

		return params;
	}

	private static List<FilterItem> matchFilterItems(@Nullable String query, @NotNull Filter available) {
		if (query == null || query.isEmpty())
			return List.of(available.getFilterGroups()[0].filterItems);

		List<FilterItem> list = new ArrayList<>();
		for (String item : query.split(",")) {
			int i = item.indexOf(":", 1);
			if (i < 1)
				continue; // ignore

			int id, group;

			try {
				group = Integer.parseInt(item.substring(0, i));
				id = Integer.parseInt(item.substring(i + 1));
			} catch (NumberFormatException e) {
				continue; // ignore
			}

			for (FilterGroup g : available.getFilterGroups()) {
				if (g.identifier == group) {
					for (FilterItem f: g.filterItems) {
						if (f.getIdentifier() == id) {
							list.add(f);
							break;
						}
					}
					break;
				}
			}
		}

		return list;
	}
}
