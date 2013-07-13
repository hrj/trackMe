package com.uprootlabs.trackme;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.http.AndroidHttpClient;

@SuppressLint("NewApi")
final class GzipHelper {
	    /**
	     * Converts an InputStream to String
	     * 
	     * @param is
	     * @return
	     * @throws IOException
	     */
	    public static String streamToString(final InputStream content) throws IOException {
	        final byte[] buffer = new byte[1024];
	        int numRead = 0;
	        final ByteArrayOutputStream baos = new ByteArrayOutputStream();

	        while ((numRead = content.read(buffer)) != -1) {
	            baos.write(buffer, 0, numRead);
	        }

	        content.close();

	        return new String(baos.toByteArray());
	    }

	    public static void setEntity(final String content, final HttpPost postReq) {
	      try {
	        postReq.setEntity(new StringEntity(content));
	      } catch (final UnsupportedEncodingException e) {
	        // TODO Auto-generated catch block
	        e.printStackTrace();
	      }
	    }
	    /**
	     * Compresses the content of the request parameters (as a string). Sets
	     * appropriate HTTP headers also so that the server can decode it properly.
	     * 
	     * @param context Context
	     * @param content The string request params, ideally JSON string
	     * @param postReq The HttpPost request object
	     * 
	     */

	    public static void setCompressedEntity(final Context context, final String content, final HttpPost postReq) {
	        try {
	            final byte[] data = content.getBytes("UTF-8");


	            // if the length of the data exceeds the minimum gzip size then only
	            // gzip it else it's not required at all
	            if (content.length() > AndroidHttpClient
	                  .getMinGzipSize(context.getContentResolver())) {
	                // set necessary headers
	                postReq.addHeader("Content-Encoding", "gzip");

	            }

	            // Compressed entity itself checks for minimum gzip size
	            // and if the content is shorter than that size then it
	            // just returns a ByteArrayEntity
	            postReq.setEntity(AndroidHttpClient.getCompressedEntity(data, context.getContentResolver()));


	        } catch (final UnsupportedEncodingException e) {
	            e.printStackTrace();
	        } catch (final IOException e) {
	            e.printStackTrace();
	        }
	    }

	    /**
	     * Extracts the response content. If the server response is compressed, then
	     * it transparently decompresses the content. In order to indicate to server
	     * that you can consume JSON response, use the following code to add the "Accept"
	     * header:
	     *
	     * AndroidHttpClient.modifyRequestToAcceptGzipResponse(HttpRequest request)
	     * 
	     * @param response
	     *                   HttpResponse Object
	     * @return String content of the HttpResponse
	     */
	    public static String getIfCompressed(final HttpResponse response) {
	        if (response == null)
	            return null;

	        try {
	            final InputStream is = AndroidHttpClient.getUngzippedContent(response.getEntity());
	            return streamToString(is);
	        } catch (final IOException e) {
	            e.printStackTrace();
	        }

	        return null;
	    }
	}