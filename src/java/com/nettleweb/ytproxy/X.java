package com.nettleweb.ytproxy;

import sun.misc.*;

import java.lang.reflect.*;

final class X {
	public static final Unsafe U;

	static {
		Unsafe unsafe;

		try {
			Field f = Unsafe.class.getDeclaredField("theUnsafe");
			f.setAccessible(true);
			unsafe = (Unsafe) f.get(null);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		U = unsafe;
	}

	private X() {}
}