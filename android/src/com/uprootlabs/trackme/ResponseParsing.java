package com.uprootlabs.trackme;

import java.io.IOException;
import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import android.net.ParseException;
import android.util.Log;

final class ResponseParsing {

  public static Document getDomElement(final String xml) {
    Document doc = null;
    final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    try {

      final DocumentBuilder db = dbf.newDocumentBuilder();

      final InputSource is = new InputSource();
      is.setCharacterStream(new StringReader(xml));
      doc = db.parse(is);

    } catch (final ParserConfigurationException e) {
      Log.e("Error: ", e.getMessage());
      return null;
    } catch (final SAXException e) {
      Log.e("Error: ", e.getMessage());
      return null;
    } catch (final IOException e) {
      Log.e("Error: ", e.getMessage());
      return null;
    }
    return doc;
  }

  public static String getXML(final HttpResponse response) {

    final HttpEntity entity = response.getEntity();

    try {
      return EntityUtils.toString(entity);
    } catch (final ParseException e) {
      e.printStackTrace();
      return "";
    } catch (final IOException e) {
      e.printStackTrace();
      return "";
    }

  }

}
