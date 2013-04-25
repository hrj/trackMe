package com.uproot.trackme

import java.io.IOException
import java.security.Principal
import java.util.Enumeration
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import com.google.appengine.api.datastore.DatastoreService
import com.google.appengine.api.datastore.DatastoreServiceFactory
import com.google.appengine.api.datastore.Entity
import com.google.appengine.api.datastore.Key
import com.google.appengine.api.datastore.KeyFactory
import com.google.appengine.api.datastore.Query
import com.google.appengine.api.datastore.Query.Filter
import com.google.appengine.api.datastore.Query.FilterOperator
import com.google.appengine.api.datastore.Query.FilterPredicate
import com.google.appengine.api.users.UserService
import com.google.appengine.api.users.UserServiceFactory
import com.google.appengine.api.datastore.Query.CompositeFilter
import com.google.appengine.api.datastore.Query.CompositeFilterOperator
import java.util.zip.GZIPInputStream
import collection.JavaConverters._
import com.google.appengine.api.datastore.EntityNotFoundException

class CommonFunctions(req: HttpServletRequest) {

  private val USER_ID = "userID"
  private val USER_DETAILS = "userDetails"
  private val PASS_KEY = "passKey"
  private val FILE_NOT_FOUND = <p>File Not Found</p>
  private val userService = UserServiceFactory.getUserService
  private val thisURL = req.getRequestURI
  private val userPrincipal = req.getUserPrincipal
  private val datastore = DatastoreServiceFactory.getDatastoreService

  val fileNotFound = XmlContent(createTemplate(FILE_NOT_FOUND), 404)

  val requestPath = ((req.getPathInfo()).split("/")).filter(_.length != 0).toList

  def userSettingsForm(userId: String, passKey: String) =
    <form action="/web/settings" method="post">
      <label>UserId : { userId }</label>
      <label>
        Pass Key :<input type="text" name="passKey" value={ passKey }/>
      </label><br/>
      <input type="submit"/>
    </form>

  def createTemplate(body: xml.Node) = {
    <html>
      <head>
        <script src="/static/js/jquery.min.js"></script>
        <link rel="stylesheet" href="/static/style/style.css" type="text/css"></link>
        <script src="/static/js/OpenLayers.js"></script>
        <script src="/static/js/locationsDisplay.js"></script>
      </head>
      <body>
        <ul>
          <li><a href="/web/home">Home</a></li>
          <li><a href="/web/settings">Settings</a></li>
          <li><a href={ userService.createLogoutURL(thisURL) }>Logout</a></li>
        </ul>
        <p>Wecome!</p>
        { body }
      </body>
    </html>
  }

  def updateSettings() = {
    val userID = req.getUserPrincipal.getName
    val passKey = req.getParameter("passKey")
    val userKey = KeyFactory.createKey(USER_DETAILS, userID)
    val userEntity = new Entity(userKey)
    userEntity.setProperty(USER_ID, userID)
    userEntity.setProperty("passKey", passKey)
    datastore.put(userEntity)
    settingsPage
  }

  def settingsPage() = {
    val userId = req.getUserPrincipal.getName
    val settingsForm =
      if (userExists(userId)) {
        val userKey = KeyFactory.createKey(USER_DETAILS, userId)
        val userEntity = datastore.get(userKey)
        val passKey = userEntity.getProperty("passKey").toString
        userSettingsForm(userId, passKey)
      } else {
        (userSettingsForm(userId, ""))
      }
    XmlContent(createTemplate(settingsForm))
  }

  def webAuthentication(f: (String) => Result) = {
    if (userPrincipal != null) {
      val userId = userPrincipal.getName
      f(userId)
    } else {
      XmlContent(createTemplate(<p>Please <a href={ userService.createLoginURL(thisURL) }>sign in</a></p>))
    }
  }

  def apiAuthentication(format: String, f: => Result) = {
    if (userPrincipal != null || (req.getHeader("userID") != null && req.getHeader("passkey") != null)) {
      f
    } else {
      format match {
        case "xml" => XmlContent(ResponseStatus(false, "User Does not Exist").mkXML)
        case "json" => JsonContent(ResponseStatus(false, "Cannot Retrieve as the user does not exists!").mkJson)
      }

    }
  }

  def userExists(userId: String) = {
    val userKey = KeyFactory.createKey(USER_DETAILS, userId)
    try {
      val userEntity = datastore.get(userKey)
      true
    } catch {
      case _ => false
    }
  }

  def homePage(userId: String) = {
    if (userExists(userId)) {
      XmlContent(createTemplate(
        xml.Group(Seq(<div id="map" class="bigmap"></div>, <input type="button" value="Refresh" id="mapUpdate"></input>))))
    } else {
      Redirect("/web/settings")
    }
  }

  def storeLocations() = {
    val inputStream = req.getHeader("Content-Encoding") match {
      case "gzip" => new GZIPInputStream(req.getInputStream)
      case _ => req.getInputStream
    }

    val sessionDetails = xml.XML.load(inputStream)
    val sessionDet = new Session(sessionDetails)

    val userKey = KeyFactory.createKey(USER_DETAILS, sessionDet.userID)
    try {
      val userEntity = datastore.get(userKey)
      val dsUserID = userEntity.getProperty("userID")
      val dsPassKey = userEntity.getProperty("passKey")
      if (dsUserID == sessionDet.userID && dsPassKey == sessionDet.passKey) {
        val locationEntities: List[Entity] = sessionDet.locationDetails.map { loc =>
          val userID = new Entity("tmUser_" + sessionDet.userID)
          userID.setProperty("latitude", loc.latLong.latitude)
          userID.setProperty("longitude", loc.latLong.longitude)
          userID.setProperty("accuracy", loc.accuracy)
          userID.setProperty("timeStamp", loc.timeStamp)
          userID
        }
        datastore.put(locationEntities.asJava)
        XmlContent(ResponseStatus(true, "Location added successfuly").mkXML)
      } else {
        XmlContent(ResponseStatus(false, "Wrong UserID or PassKey").mkXML)
      }
    } catch {
      case _ => XmlContent(ResponseStatus(false, "User Does not Exist").mkXML)
    }
  }

  def retrieveLocations = {
    val userId = req.getUserPrincipal.getName
    val userKey = KeyFactory.createKey(USER_DETAILS, userId)
    try {
      val userEntity = datastore.get(userKey)
      val query = new Query("tmUser_" + userId)
      val pq = datastore.prepare(query).asIterable.asScala
      val locations = pq.map { location =>
        LatLong(location.getProperty("longitude").asInstanceOf[Double], location.getProperty("latitude").asInstanceOf[Double])
      }
      JsonContent("{\"locations\":[" + (locations.map(_.mkJSON)).mkString(",") + "]}")
    } catch {
      case _ => JsonContent(ResponseStatus(false, "Cannot Retrieve as the user does not exists!").mkJson)
    }
  }

  def sendResponse(result: Result, resp: HttpServletResponse) {
    result match {
      case c: Content => {
        resp.setStatus(c.responseCode)
        resp.setContentType(c.contentType)
        resp.getWriter.write(c.content)
        resp.flushBuffer
      }
      case Redirect(url) => {
        resp.sendRedirect(url.toString)
      }
    }
  }

}
