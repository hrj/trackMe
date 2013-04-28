package com.uproot.trackme;


object implicitObject {
  implicit class XMLHelper (n: xml.Node) {
    def attr(name: String) = n.attribute(name).get(0).text
    def attrDouble(name: String) = n.attr(name).toDouble
    def attrInt(name: String) = n.attr(name).toInt
    def attrLong(name: String) = n.attr(name).toLong
  }
}

import implicitObject._

case class Location(latLong: LatLong, accuracy: Int, timeStamp: Long) {
  def this(locDetails: scala.xml.Node) = this(LatLong(locDetails.attrDouble("latitude"), locDetails.attrDouble("longitude")),
    locDetails.attrInt("accuracy"), locDetails.attrLong("timestamp"))
}

case class Session(id: String, userID: String, passKey: String, locationDetails: List[Location]) {
  def this(node: scala.xml.Elem) = this(node.attr("id"), node.attr("userid"), node.attr("passkey"),
    (node \ "location").toList.map(new Location(_)))
}

case class LatLong(latitude: Double, longitude: Double) {
  def mkJSON = "{\"lat\":" + latitude + ", \"long\":" + longitude + "}"
}
