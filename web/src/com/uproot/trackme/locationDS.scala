package com.uproot.trackme;

import Helper._

object implicitObject {
  implicit class XMLHelper(n: xml.Node) {
    def attr(name: String) = n.attribute(name).get(0).text
    def attrDouble(name: String) = n.attr(name).toDouble
    def attrInt(name: String) = n.attr(name).toInt
    def attrLong(name: String) = n.attr(name).toLong
  }
}

import implicitObject._

object Constants {
  val ACCURACY_LIMIT = 3000
  val PI = math.Pi
  val MINUS_PI = -PI
  val PIby2 = PI / 2
  val MINUS_PIby2 = -PIby2
}

case class Location(latLong: LatLong, accuracy: Long, timeStamp: Long) {
  def this(locDetails: scala.xml.Node) = this(LatLong(locDetails.attrDouble(XML_ATTRIBUTE_LATITUDE), locDetails.attrDouble(XML_ATTRIBUTE_LONGITUDE)),
    locDetails.attrLong(XML_ATTRIBUTE_ACCURACY), locDetails.attrLong(XML_ATTRIBUTE_TIME_STAMP))

  def isValid(maxTime: Long) = {
    latLong.isValid && accuracy < Constants.ACCURACY_LIMIT && timeStamp < maxTime
  }

  def mkJSON = "{\"lat\":" + latLong.latitude + ", \"long\":" + latLong.longitude + ", \"ts\":" + timeStamp + ", \"acc\":" + accuracy + "}"
}

case class Upload(uploadid: Int, userid: String, passkey: String, batches: List[Batch]) {
  def this(upload: scala.xml.Elem) = this(upload.attrInt(XML_ATTRIBUTE_UPLOAD_ID), upload.attr(XML_ATTRIBUTE_USER_ID), upload.attr(XML_ATTRIBUTE_PASS_KEY), (upload \ XML_TAG_BATCH).toList.map(new Batch(_)));
}

case class Batch(sid: String, bid: Int, locations: List[Location]) {
  def this(node: scala.xml.Node) = this(node.attr(XML_ATTRIBUTE_SESSION_ID), node.attrInt(XML_ATTRIBUTE_BATCH_ID), (node \ XML_TAG_LOCATION).toList.map(new Location(_)))
}

case class LatLong(latitude: Double, longitude: Double) {
  def isValid = {
    latitude >= Constants.MINUS_PIby2 && latitude <= Constants.PIby2 &&
      longitude >= Constants.MINUS_PI && longitude <= Constants.PI
  }

  def mkJSON = "{\"lat\":" + latitude + ", \"long\":" + longitude + "}"
}
