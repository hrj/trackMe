package com.uproot.trackme

import java.util.ArrayList
import java.util.zip.GZIPInputStream
import scala.Option.option2Iterable
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import com.google.appengine.api.datastore.DatastoreServiceFactory
import com.google.appengine.api.datastore.Entity
import com.google.appengine.api.datastore.FetchOptions
import com.google.appengine.api.datastore.KeyFactory
import com.google.appengine.api.datastore.Query
import com.google.appengine.api.datastore.Query.FilterOperator
import com.google.appengine.api.datastore.Query.FilterPredicate
import com.google.appengine.api.datastore.Query.SortDirection
import com.google.appengine.api.users.UserServiceFactory
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import com.sun.org.apache.xerces.internal.impl.dv.ValidatedInfo
import com.google.appengine.api.datastore.EntityNotFoundException

class CommonFunctions(req: HttpServletRequest) {
  import Helper._
  private val userPrincipal = req.getUserPrincipal
  private val userService = UserServiceFactory.getUserService
  private val thisURL = req.getRequestURI
  private val datastore = DatastoreServiceFactory.getDatastoreService
  private val logoutURL = userService.createLogoutURL(thisURL)
  private val currUserId = if (userPrincipal != null) userPrincipal.getName else null
  private val userExists = {
    if (currUserId != null) {
      try {
        val userKey = KeyFactory.createKey(USER_DETAILS, currUserId)
        datastore.get(userKey)
        true
      } catch {
        case _: IllegalArgumentException | _: EntityNotFoundException => false
      }
    } else {
      false
    }
  }

  private def createTemplate(message: xml.Node, jScript: Option[String] = None) = {
    Helper.createTemplate("Guest!", message, jScript, logoutURL = logoutURL)
  }
  val fileNotFound = XmlContent(createTemplate(FILE_NOT_FOUND), 404)

  val requestPath = ((req.getPathInfo()).split("/")).filter(_.length != 0).toList

  def webAuthentication(f: (LoggedIn) => Result) = {
    if (currUserId != null) {
      f(new LoggedIn(currUserId, req))
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

  private def mkXmlLinkList(listElements: Seq[String], message: Option[String] = None) = {
    if (listElements.nonEmpty) {
      listElements.map(element => <li><a href={ "/web/user/" + element }>{ element }</a></li>)
    } else {
      message.map(message => <li>{ message }</li>).getOrElse(xml.Null)
    }
  }

  private def mkXmlList(listElements: Seq[String], message: Option[String] = None) = {
    if (listElements.nonEmpty) {
      (listElements).map(element => <li>{ element }</li>)
    } else {
      message.map(message => <li>{ message }</li>).getOrElse(xml.Null)
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

  def storeLocations() = {
    val inputStream = req.getHeader("Content-Encoding") match {
      case "gzip" => new GZIPInputStream(req.getInputStream)
      case _ => req.getInputStream
    }

    val sessionDetails = xml.XML.load(inputStream)
    val sessionDet = new Session(sessionDetails)
    val locations = sessionDet.locationDetails
    val maxTime = System.currentTimeMillis + GRACE_PERIOD
    if (locations.forall(_.isValid(maxTime))) {
      try {
        val userKey = KeyFactory.createKey(USER_DETAILS, sessionDet.userID)
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
        case _: IllegalArgumentException | _: EntityNotFoundException => XmlContent(ResponseStatus(false, "User Does not Exist").mkXML)
      }
    } else {
      XmlContent(ResponseStatus(false, "Invalid Locations").mkXML)
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
      if (sharedFrom(currUserId).contains(userId) || userId == currUserId) {
        val url = "var retrieveURL = \"/web/getuserlocations/" + userId + "\""
        XmlContent(createTemplate(
          xml.Group(Seq(<div id="map" class="bigmap"></div>,
            <p>
              <h3>Shared From</h3>
              <ul>{ mkXmlLinkList(sharedFrom(currUserId), Some("No Shares!")) }</ul>
            </p>)), Some(url)))
      } else {
        XmlContent(createTemplate(<b>The user does not share his locations with you!</b>))
      }
    } else {
      Redirect("/web/home")
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
object Helper {

  val USER_ID = "userID"
  val PASS_KEY = "passKey"
  val LOCATIONS = "locations"
  val SHARED_WITH = "sharedWith"
  val VERSION_NO = "versionNo"
  val FILE_NOT_FOUND = <p>File Not Found</p>
  val GRACE_PERIOD = 3600000
  val USER_DETAILS = "userDetails"
  val datastore = DatastoreServiceFactory.getDatastoreService
  def userExistsFunc(userId: String) = {
    try {
      val userKey = KeyFactory.createKey(USER_DETAILS, userId)
      datastore.get(userKey)
      true
    } catch {
      case _: IllegalArgumentException | _: EntityNotFoundException => false
    }
  }

  def getUserEntity(userId: String) = {
    datastore.get(mkUserKey(userId))
  }
  def mkUserKey(userId: String) = {
    KeyFactory.createKey(USER_DETAILS, userId)
  }

  def createTemplate(userName: String, body: xml.Node, jScript: Option[String] = None, logoutURL: String) = {
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
          <li><a href={ logoutURL }>Logout</a></li>
        </ul>
        <p>Welcome! { userName }</p>
        { body }
      </body>
    </html>
  }
}

class LoggedIn(currUserId: String, req: HttpServletRequest) {

  import Helper._
  private val userExists = userExistsFunc(currUserId)
  private val userService = UserServiceFactory.getUserService
  private val thisURL = req.getRequestURI
  private val logoutURL = userService.createLogoutURL(thisURL)

  private def createTemplate(message: xml.Node, jScript: Option[String] = None) = {
    Helper.createTemplate("Guest!", message, jScript, logoutURL = logoutURL)
  }

  def updateSettings() = {
    val updateVersionNo = req.getParameter("versionNo").toLong
    val passKey = req.getParameter("passKey")
    val shareWith = req.getParameter("shareWith")
    val usersSharingWith = shareWith.split(",").toList
    val (validUsers, invalidUsers) = usersSharingWith.partition(userExistsFunc(_))
    val userKey = mkUserKey(currUserId)
    val (userEntity, serverVersionNo) = if (userExists) {
      val userEntity = getUserEntity(currUserId)
      (userEntity, userEntity.getProperty("versionNo").asInstanceOf[Long])
    } else {
      (new Entity(userKey), 0.toLong)
    }
    val message = if (serverVersionNo == updateVersionNo) {
      userEntity.setProperty(VERSION_NO, serverVersionNo + 1.toLong)
      userEntity.setProperty(PASS_KEY, passKey)
      if (validUsers.nonEmpty) {
        userEntity.setProperty(SHARED_WITH, (validUsers).asJava)
      } else {
        userEntity.removeProperty(SHARED_WITH)
      }
      datastore.put(userEntity)
      "Settings Updated Successfully!" + {
        if (invalidUsers.nonEmpty) {
          " Failed to share with the folling Users as they are not registered Users: " + invalidUsers.mkString(" , ")
        } else {
          ""
        }
      }
    } else {
      "Update Failed! Attempt to update outdated information."
    }
    settingsPage(Some(message))
  }

  private def userSettingsForm(passKey: String, sharedWith: String, message: Option[String], versionNo: Long) =
    <form action="/web/settings" method="post">
      {
        message.map { message =>
          <br/><b>{ xml.Unparsed(message) }</b><br/>
        }.getOrElse(xml.Null)
      }
      <input type="hidden" name="versionNo" value={ versionNo.toString }/>
      <label>UserId : { currUserId }</label><br/>
      <label>
        Pass Key :<input type="text" name="passKey" value={ passKey }/>
      </label><br/>
      <label>
        Share With :<input type="text" name="shareWith" value={ sharedWith }/>
      </label><br/>
      <input type="submit"/>
    </form>

  def settingsPage(message: Option[String] = None) = {
    val settingsForm =
      if (userExists) {
        val userKey = KeyFactory.createKey(USER_DETAILS, currUserId)
        val userEntity = datastore.get(userKey)
        val passKey = userEntity.getProperty(PASS_KEY).toString
        val versionNo = userEntity.getProperty(VERSION_NO).asInstanceOf[Long]
        val shared = if (userEntity.hasProperty(SHARED_WITH)) {
          val sharedWith = userEntity.getProperty(SHARED_WITH)
          sharedWith.asInstanceOf[ArrayList[String]].asScala.mkString(",")
        } else {
          ""
        }
        userSettingsForm(passKey, shared, message, versionNo)
      } else {
        (userSettingsForm("", "", Some("Please Set your PassKey"), 1))
      }
    XmlContent(createTemplate(settingsForm))
  }

  private def mkXmlLinkList(listElements: Seq[String], message: Option[String] = None) = {
    if (listElements.nonEmpty) {
      listElements.map(element => <li><a href={ "/web/user/" + element }>{ element }</a></li>)
    } else {
      message.map(message => <li>{ message }</li>).getOrElse(xml.Null)
    }
  }

  private def mkXmlList(listElements: Seq[String], message: Option[String] = None) = {
    if (listElements.nonEmpty) {
      (listElements).map(element => <li>{ element }</li>)
    } else {
      message.map(message => <li>{ message }</li>).getOrElse(xml.Null)
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
      <ul>{ mkXmlList(sharedWith(userId), Some("No Shares!")) }</ul>
      <h3>Shared From</h3>
      <ul>{ mkXmlLinkList(sharedFrom(userId), Some("No Shares!")) }</ul>
    </p>
  }

  def homePage() = {
    if (userExists) {
      XmlContent(createTemplate(
        xml.Group(Seq(<div id="map" class="bigmap"></div>,
          <input type="button" value="Refresh" id="mapUpdate"></input>,
          getSharingDetails(currUserId))), Some("var retrieveURL = \"/api/json/retrieve\"")))

    } else {
      Redirect("/web/settings")
    }
  }
}
