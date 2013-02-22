package com.dwdesign.tweetings.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.w3c.dom.Document;

import com.dwdesign.tweetings.Constants;
import com.dwdesign.tweetings.HttpClientFactory;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;

public class TwitlongerAsyncTask implements Constants {

	private final Context mContext; 
	private final String mUrl;

    public TwitlongerAsyncTask(final Context context, final String url) {
        super();
        this.mContext = context;
        this.mUrl = url;
        this.doExpand(mUrl);
    }
    
    protected void doExpand(final String url) {
    	final String finalUrl = "http://www.twitlonger.com/api_read/" + url.replace("http://tl.gd/", "");
    	Thread thread = new Thread() {
		      public void run() {
		    	  try {
		    		  HttpClient httpclient = HttpClientFactory.getThreadSafeClient();
		    		  HttpResponse response = httpclient.execute(new HttpGet(finalUrl));
		    		  DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
		  			domFactory.setNamespaceAware(true); // never forget this!
		  			DocumentBuilder builder = domFactory.newDocumentBuilder();
		  			Document doc = builder.parse(response.getEntity().getContent());
		  			
		  			XPathFactory factory = XPathFactory.newInstance();
		  			XPath xpath = factory.newXPath();
		  			XPathExpression expr, expr2;
		  			expr = xpath.compile("//twitlonger/post/content/text()");
		  			expr2 = xpath.compile("//twitlonger/post/user/text()");
		  			
		  			Object result = expr.evaluate(doc, XPathConstants.STRING);
		  			Object result2 = expr2.evaluate(doc, XPathConstants.STRING);
		  			
		  			final Intent intent = new Intent(BROADCAST_TWITLONGER_EXPANDED);
		        	intent.putExtra(INTENT_KEY_TWITLONGER_EXPANDED_TEXT, result.toString());
		        	intent.putExtra(INTENT_KEY_TWITLONGER_ORIGINAL_URL, url);
		        	intent.putExtra(INTENT_KEY_TWITLONGER_USER, result2.toString());
		    		mContext.sendBroadcast(intent);
		    	  }
		    	  catch (Exception e) {
		    	  }
		      }
    	};
    	thread.start();
	
    }

}
