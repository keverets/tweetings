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

package com.dwdesign.gallery3d.data;

import static com.dwdesign.tweetings.util.Utils.getImageLoaderHttpClient;
import static com.dwdesign.tweetings.util.Utils.getRedirectedHttpResponse;
import static com.dwdesign.tweetings.util.Utils.parseString;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.URL;

import com.dwdesign.gallery3d.common.Utils;
import com.dwdesign.gallery3d.util.ThreadPool.CancelListener;
import com.dwdesign.gallery3d.util.ThreadPool.JobContext;

import twitter4j.internal.http.HttpClientWrapper;
import android.content.Context;
import android.util.Log;

public class DownloadUtils {
	private static final String TAG = "DownloadService";

	public static boolean download(final Context context, final JobContext jc, final URL url, final OutputStream output) {
		InputStream input = null;
		try {
			final HttpClientWrapper client = getImageLoaderHttpClient(context);
			input = getRedirectedHttpResponse(client, parseString(url)).asStream();
			dump(jc, input, output);
			return true;
		} catch (final Throwable t) {
			Log.w(TAG, "fail to download", t);
			return false;
		} finally {
			Utils.closeSilently(input);
		}
	}

	public static void dump(final JobContext jc, final InputStream is, final OutputStream os) throws IOException {
		final byte buffer[] = new byte[4096];
		int rc = is.read(buffer, 0, buffer.length);
		final Thread thread = Thread.currentThread();
		jc.setCancelListener(new CancelListener() {
			@Override
			public void onCancel() {
				thread.interrupt();
			}
		});
		while (rc > 0) {
			if (jc.isCancelled()) throw new InterruptedIOException();
			os.write(buffer, 0, rc);
			rc = is.read(buffer, 0, buffer.length);
		}
		jc.setCancelListener(null);
		Thread.interrupted(); // consume the interrupt signal
	}

	public static boolean requestDownload(final Context context, final JobContext jc, final URL url, final File file) {
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(file);
			return download(context, jc, url, fos);
		} catch (final Throwable t) {
			return false;
		} finally {
			Utils.closeSilently(fos);
		}
	}
}