package com.uproot.trackme

import java.security.Principal
import java.util.ArrayList
import java.util.zip.GZIPInputStream
import scala.collection.JavaConverters._
import com.google.appengine.api.datastore.DatastoreService
import com.google.appengine.api.datastore.DatastoreServiceFactory
import com.google.appengine.api.datastore.Entity
import com.google.appengine.api.datastore.Key
import com.google.appengine.api.datastore.KeyFactory
import com.google.appengine.api.datastore.Query
import com.google.appengine.api.datastore.Query.FilterOperator
import com.google.appengine.api.datastore.Query.FilterPredicate
import com.google.appengine.api.users.UserService
import com.google.appengine.api.users.UserServiceFactory
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import com.google.appengine.api.datastore.Query.CompositeFilterOperator
import com.google.appengine.api.datastore.EntityNotFoundException
import com.google.appengine.api.datastore.Query.SortDirection
import com.google.appengine.api.datastore.FetchOptions

class CommonFunctions(req: HttpServletRequest) {

  private val USER_ID = "userID"
  private val USER_DETAILS = "userDetails"
  private val PASS_KEY = "passKey"
  private val LOCATIONS = "locations"
  private val SHARED_WITH = "sharedWith"
  private val FILE_NOT_FOUND = <p>File Not Found</p>
  private val userService = UserServiceFactory.getUserService
  private val thisURL = req.getRequestURI
  private val userPrincipal = req.getUserPrincipal
  private val datastore = DatastoreServiceFactory.getDatastoreService
  private val currUserId = if (userPrincipal != null) userPrincipal.getName else null
  private val userExists = {
    if (currUserId != null) {
      val userKey = KeyFactory.createKey(USER_DETAILS, currUserId)
      try {
        val userEntity = datastore.get(userKey)
        true
      } catch {
        case _ => false
      }
    } else {
      false
    }
  }

  private def userExistsFunc(userId: String) = {
    val userKey = KeyFactory.createKey(USER_DETAILS, userId)
    try {
      val userEntity = datastore.get(userKey)
      true
    } catch {
      case _ => false
    }
  }

  val fileNotFound = XmlContent(createTemplate(FILE_NOT_FOUND), 404)

  val requestPath = ((req.getPathInfo()).split("/")).filter(_.length != 0).toList

  private def userSettingsForm(userId: String, passKey: String, sharedWith: String) =
    <form action="/web/settings" method="post">
      <label>UserId : { userId }</label>
      <label>
        Pass Key :<input type="text" name="passKey" value={ passKey }/>
      </label><br/>
      <label>
        Share With :<input type="text" name="shareWith" value={ sharedWith }/>
      </label><br/>
      <input type="submit"/>
    </form>

  private def createTemplate(body: xml.Node, jScript: Option[String] = None) = {
    val userId = if (currUserId == null) "Guest" else currUserId
    <html>
      <head>
        <script src="/static/js/jquery-1.9.1.min.js"></script>
        <link rel="stylesheet" href="/static/style/style.css" type="text/css"></link>
        {
          jScript.map { script =>
            <script>{ xml.Unparsed(script) }</script>
          }.getOrElse(xml.Null)
        }
        <script src="/static/js/OpenLayers.js"></script>
        <script src="/static/js/locationsDisplay.js"></script>
      </head>
      <body>
        <ul>
          <li><a href="/web/home">Home</a></li>
          <li><a href="/web/settings">Settings</a></li>
          <li><a href={ userService.createLogoutURL(thisURL) }>Logout</a></li>
        </ul>
        <p>Welcome! { userId }</p>
        { body }
      </body>
    </html>
  }

  def updateSettings() = {
    val userKey = KeyFactory.createKey(USER_DETAILS, currUserId)
    val passKey = req.getParameter("passKey")
    val shareWith = req.getParameter("shareWith")
    val userEntity = new Entity(userKey)
    userEntity.setProperty(USER_ID, currUserId)
    userEntity.setProperty("passKey", passKey)
    if (shareWith.length != 0) {
      userEntity.setProperty(SHARED_WITH, (shareWith.split(",").toList).asJava)
    }
    datastore.put(userEntity)
    settingsPage
  }

  def settingsPage() = {
    val settingsForm =
      if (userExists) {
        val userKey = KeyFactory.createKey(USER_DETAILS, currUserId)
        val userEntity = datastore.get(userKey)
        val passKey = userEntity.getProperty(PASS_KEY).toString
        val shared = if (userEntity.hasProperty(SHARED_WITH)) {
          val sharedWith = userEntity.getProperty(SHARED_WITH)
          sharedWith.asInstanceOf[ArrayList[String]].asScala.mkString(",")
        } else {
          ""
        }
        userSettingsForm(currUserId, passKey, shared)
      } else {
        (userSettingsForm(currUserId, "", ""))
      }
    XmlContent(createTemplate(settingsForm))
  }

  def webAuthentication(f: (String) => Result) = {
    if (currUserId != null) {
      f(currUserId)
    } else {
      XmlContent(createTemplate(<p>Please <a href={ userService.createLoginURL(thisURL) }>sign in</a></p>))
    }
  }

  def apiAuthentication(format: String, f: => Result) = {
    if (userPrincipal != null || (req.getHeader(USER_ID) != null && req.getHeader("passkey") != null)) {
      f
    } else {
      format match {
        case "xml" => XmlContent(ResponseStatus(false, "User Does not Exist").mkXML)
        case "json" => JsonContent(ResponseStatus(false, "Cannot Retrieve as the user does not exists!").mkJson)
      }

    }
  }

  private def mkXMLUserList(userList: Seq[String]) = {
    if (userList.nonEmpty) {
      (userList).map(user => <li>{ user }</li>)
    } else {
      <li>No Shares!</li>
    }
  }

  private def sharedWith(userId: String) = {
    val userEntity = datastore.get(KeyFactory.createKey(USER_DETAILS, userId))
    if (userEntity.hasProperty(SHARED_WITH)) {
      val shared = userEntity.getProperty(SHARED_WITH).asInstanceOf[ArrayList[String]]
      shared.asScala.toSeq
    } else {
      Nil
    }
  }

  private def sharedFrom(userId: String) = {
    val shareFromFilter = new FilterPredicate(SHARED_WITH, FilterOperator.EQUAL, userId)
    val q = new Query(USER_DETAILS).setFilter(shareFromFilter)
    val usersSharedFrom = datastore.prepare(q).asIterable.asScala.toSeq
    usersSharedFrom.map(_.getProperty(USER_ID).asInstanceOf[String])
  }

  private def getSharingDetails(userId: String) = {
    <p>
      <h3>Shared With</h3>
      <ul>{ mkXMLUserList(sharedWith(userId)) }</ul>
      <h3>Shared From</h3>
      <ul>{ mkXMLUserList(sharedFrom(userId)) }</ul>
    </p>
  }

  def homePage(userId: String) = {
    if (userExists) {
      XmlContent(createTemplate(
        xml.Group(Seq(<div id="map" class="bigmap"></div>,
          <input type="button" value="Refresh" id="mapUpdate"></input>,
          getSharingDetails(userId))), Some("var retrieveURL = \"/api/json/retrieve\"")))
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
          val userLocations = new Entity(LOCATIONS, userKey)
          userLocations.setProperty("latitude", loc.latLong.latitude)
          userLocations.setProperty("longitude", loc.latLong.longitude)
          userLocations.setProperty("accuracy", loc.accuracy)
          userLocations.setProperty("timeStamp", loc.timeStamp)
          userLocations
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

  private def quote(value: String) = "\"" + value + "\""

  private def sharedLocationsMkJson(sharedLocations: Seq[(String, LatLong)]) = {
    "\"sharedLocations\":" + sharedLocations.map { user =>
      quote(user._1) + ":" + user._2.mkJSON
    }.mkString("{", ",", "}")
  }

  private def getLastLocations(userId: String) = {
    sharedFrom(userId).flatMap { sharerId =>
      val userKey = KeyFactory.createKey(USER_DETAILS, sharerId)
      val userQuery = new Query(LOCATIONS).setAncestor(userKey) addSort ("timeStamp", SortDirection.DESCENDING)
      val usersLastLocation = ((datastore.prepare(userQuery)).asList(FetchOptions.Builder.withLimit(1))).asScala.headOption
      usersLastLocation.map { location =>
        (sharerId, LatLong(location.getProperty("latitude").asInstanceOf[Double], location.getProperty("longitude").asInstanceOf[Double]))
      }
    }
  }

  private def getLocations(userId: String) = {
    val userKey = KeyFactory.createKey(USER_DETAILS, userId)
    val query = new Query(LOCATIONS).setAncestor(userKey)
    val locations = datastore.prepare(query).asList(FetchOptions.Builder.withLimit(10)).asScala
    (locations.map { location =>
      (LatLong(location.getProperty("latitude").asInstanceOf[Double], location.getProperty("longitude").asInstanceOf[Double])).mkJSON
    }).mkString("\"locations\":[", ",", "]")
  }

  def getUserLocations(userId: String) = {
    if (userId == currUserId || userExistsFunc(userId)) {
      JsonContent("{" + getLocations(userId) + "}", 200)
    } else {
      JsonContent(ResponseStatus(false, "Cannot Retrieve as there are no locations stored for this user!").mkJson, 401)
    }
  }

  def retrieveLocations = {
    if (userExists) {
      val myLocations = getLocations(currUserId)
      val sharedLocations = sharedLocationsMkJson(getLastLocations(currUserId))
      JsonContent(List(myLocations, sharedLocations).mkString("{", ",", "}"))
    } else {
      JsonContent(ResponseStatus(false, "Cannot Retrieve as there are no locations stored for this user!").mkJson, 403)
    }
  }

  def viewLocations(userId: String) = {
    if (userExists) {
      val url = "var retrieveURL = \"/web/getuserlocations/" + userId + "\""
      XmlContent(createTemplate(
        xml.Group(Seq(<div id="map" class="bigmap"></div>,
          getSharingDetails(userId))), Some(url)))
    } else {
      Redirect("/web/settings")
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
