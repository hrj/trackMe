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

  val USER_ID = "userID"
  val USER_DETAILS = "userDetails"
  val PASS_KEY = "passKey"
  val FILE_NOT_FOUND = <p>File Not Found</p>
  val userService = UserServiceFactory.getUserService
  val thisURL = req.getRequestURI
  val userPrincipal = req.getUserPrincipal
  val datastore = DatastoreServiceFactory.getDatastoreService

  def requestPath = ((req.getPathInfo()).split("/")).filter(_.length != 0).toList

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

  def fileNotFound = createTemplate(FILE_NOT_FOUND)

  def updateSettings() = {
    val userID = req.getUserPrincipal.getName
    val passKey = req.getParameter("passKey")
    val trackMeKey = KeyFactory.createKey(USER_DETAILS, userID)
    val userDetails = new Entity(trackMeKey)
    userDetails.setProperty(USER_ID, userID)
    userDetails.setProperty("passKey", passKey)
    datastore.put(userDetails)
    settingsPage
  }

  def settingsPage() = {
    val userId = req.getUserPrincipal.getName
    if (userExists(userId)) {
      val userIDKey = KeyFactory.createKey(USER_DETAILS, userId)
      val userEntity = datastore.get(userIDKey)
      println(userEntity)
      val passKey = userEntity.getProperty("passKey").toString
      XmlContent(createTemplate(
        <form action="/web/settings" method="post">
          <label>UserId : <input type="text" value={ userId }/></label>
          <label>Pass Key : <input type="text" name="passKey" value={ passKey }/></label><br/>
          <input type="submit"/>
        </form>))
    } else {
      XmlContent(createTemplate(
        <form action="/web/settings" method="post">
          <label>UserId : <input type="text" value={ userId }/></label>
          <label>Pass Key : <input type="text" name="passKey"/></label><br/>
          <input type="submit"/>
        </form>))
    }

  }

  def webAuthentication(f: (String) => Result) = {
    if (userPrincipal != null) {
      val userId = userPrincipal.getName
      f(userId)
    } else {
      XmlContent(createTemplate(<p>Please <a href={ userService.createLoginURL(thisURL) }>sign in</a></p>))
    }
  }

  def apiAuthentication(f: => Result) = {
    if (userPrincipal != null || (req.getHeader("userID") != null && req.getHeader("passkey") != null)) {
      f
    } else {
      XmlContent(createTemplate(<p>User not authenticated</p>))
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

    val userExistsFilter = new FilterPredicate(USER_ID, FilterOperator.EQUAL, sessionDet.userID)

    val passKeyCheckFilter = new FilterPredicate(PASS_KEY, FilterOperator.EQUAL, sessionDet.passKey)

    val userVerificationFilter = CompositeFilterOperator.and(userExistsFilter, passKeyCheckFilter)

    val query = new Query(USER_DETAILS).setFilter(userVerificationFilter)

    val pq = datastore.prepare(query).asSingleEntity
    if (pq == null) {
      XmlContent(ResponseStatus(false, "User Does no exist or wrong PassKey").mkXML)
    } else {
      val sessionKey = KeyFactory.createKey(USER_ID, "trackMe")
      sessionDet.locationDetails.foreach { loc =>
        val userID = new Entity("tmUser_" + sessionDet.userID, sessionKey)
        userID.setProperty("latitude", loc.latLong.latitude)
        userID.setProperty("longitude", loc.latLong.longitude)
        userID.setProperty("accuracy", loc.accuracy)
        userID.setProperty("timeStamp", loc.timeStamp)
        datastore.put(userID)
      }
      XmlContent(ResponseStatus(true, "Location added successfuly").mkXML)
    }
  }

  def retrieveLocations = {
    val userId = req.getUserPrincipal.getName
    val userFilter = new FilterPredicate("userID", FilterOperator.EQUAL, userId)
    val userQuery = new Query(USER_DETAILS).setFilter(userFilter)
    val userExists = datastore.prepare(userQuery).asSingleEntity
    if (userExists != null) {
      val query = new Query(userId)
      val pq = datastore.prepare(query).asIterable.asScala
      val locations = pq.map { location =>
        LatLong(location.getProperty("longitude").toString.toDouble, location.getProperty("latitude").toString.toDouble)
      }
      JsonContent("{\"locations\":[" + (locations.map(_.mkJSON)).mkString(",") + "]}")
    } else {
      JsonContent(ResponseStatus(false, "Cannot Retrieve as the user does not exists!").mkJson)
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
