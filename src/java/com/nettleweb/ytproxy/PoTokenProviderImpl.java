package com.nettleweb.ytproxy;

import org.jetbrains.annotations.*;
import org.schabi.newpipe.extractor.services.youtube.*;

final class PoTokenProviderImpl implements PoTokenProvider {
	public static final PoTokenProviderImpl instance = new PoTokenProviderImpl();

	@TestOnly
	private PoTokenProviderImpl() {}

	@Override
	public PoTokenResult getWebClientPoToken(String videoId) {
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