package com.uproot.trackme;

sealed abstract class Result()

case class Redirect(url: String) extends Result

class Content(val contentType: String, val content: String) extends Result

case class XmlContent(xContent: xml.Node) extends Content("text/html", xContent.toString)

case class JsonContent(jContent: String) extends Content("application/json", jContent)