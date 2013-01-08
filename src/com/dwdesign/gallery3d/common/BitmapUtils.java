/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dwdesign.gallery3d.common;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.FloatMath;

public class BitmapUtils {
	public static final int UNCONSTRAINED = -1;

	private BitmapUtils() {
	}

	// Find the max x that 1 / x <= scale.
	public static int computeSampleSize(final float scale) {
		Utils.assertTrue(scale > 0);
		final int initialSize = Math.max(1, (int) FloatMath.ceil(1 / scale));
		return initialSize <= 8 ? Utils.nextPowerOf2(initialSize) : (initialSize + 7) / 8 * 8;
	}

	// Find the min x that 1 / x >= scale
	public static int computeSampleSizeLarger(final float scale) {
		final int initialSize = (int) FloatMath.floor(1f / scale);
		if (initialSize <= 1) return 1;

		return initialSize <= 8 ? Utils.prevPowerOf2(initialSize) : initialSize / 8 * 8;
	}

	public static Bitmap resizeBitmapByScale(final Bitmap bitmap, final float scale, final boolean recycle) {
		final int width = Math.round(bitmap.getWidth() * scale);
		final int height = Math.round(bitmap.getHeight() * scale);
		if (width == bitmap.getWidth() && height == bitmap.getHeight()) return bitmap;
		final Bitmap target = Bitmap.createBitmap(width, height, getConfig(bitmap));
		final Canvas canvas = new Canvas(target);
		canvas.scale(scale, scale);
		final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
		canvas.drawBitmap(bitmap, 0, 0, paint);
		if (recycle) {
			bitmap.recycle();
		}
		return target;
	}

	public static Bitmap resizeDownBySideLength(final Bitmap bitmap, final int maxLength, final boolean recycle) {
		final int srcWidth = bitmap.getWidth();
		final int srcHeight = bitmap.getHeight();
		final float scale = Math.min((float) maxLength / srcWidth, (float) maxLength / srcHeight);
		if (scale >= 1.0f) return bitmap;
		return resizeBitmapByScale(bitmap, scale, recycle);
	}

	public static Bitmap rotateBitmap(final Bitmap source, final int rotation, final boolean recycle) {
		if (rotation == 0) return source;
		final int w = source.getWidth();
		final int h = source.getHeight();
		final Matrix m = new Matrix();
		m.postRotate(rotation);
		final Bitmap bitmap = Bitmap.createBitmap(source, 0, 0, w, h, m, true);
		if (recycle) {
			source.recycle();
		}
		return bitmap;
	}

	private static Bitmap.Config getConfig(final Bitmap bitmap) {
		Bitmap.Config config = bitmap.getConfig();
		if (config == null) {
			config = Bitmap.Config.ARGB_8888;
		}
		return config;
	}
}
