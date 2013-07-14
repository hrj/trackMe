package com.uproot.trackme

import com.google.appengine.api.datastore.DatastoreServiceFactory
import com.google.appengine.api.datastore.EntityNotFoundException
import com.google.appengine.api.datastore.Key
import com.google.appengine.api.datastore.KeyFactory
import com.google.appengine.api.users.UserServiceFactory

import javax.servlet.http.HttpServletRequest
case class MenuEntry(title: String, icon: String, url: String)
class Menu(entries: Seq[MenuEntry]) {

  def createMenu(activeTitle: String) = {
    <ul class="nav nav-list">{
      entries.map {
        entry =>
          if (entry.title equals activeTitle) {
            <li class="active">
              <a href={ entry.url }><i class={ entry.icon }></i>{ entry.title }</a>
            </li>
          } else { <li><a href={ entry.url }><i class={ entry.icon }></i>{ entry.title }</a></li> }
      }
    }</ul>
  }
}

class CommonFunctions(req: HttpServletRequest) {
  import Helper._
  private val userPrincipal = req.getUserPrincipal
  private val userService = UserServiceFactory.getUserService
  private val thisURL = req.getRequestURI
  private val datastore = DatastoreServiceFactory.getDatastoreService
  private val logoutURL = userService.createLogoutURL(thisURL)

  private def createTemplate(message: xml.Node, jScript: Option[String] = None) = {
    Helper.createTemplate(<p></p>, " Guest!", message, jScript, logoutURL = logoutURL)
  }
  val fileNotFound = XmlContent(createTemplate(FILE_NOT_FOUND), 404)

  val requestPath = ((req.getPathInfo()).split("/")).filter(_.length != 0).toList

  def webAuthentication(f: (LoggedIn) => Result) = {
    if (userPrincipal != null) {
      val currUserId = userPrincipal.getName
      f(new LoggedIn(currUserId, req))
    } else {
      XmlContent(createTemplate(<p>Please <a href={ userService.createLoginURL(thisURL) }>sign in</a></p>))
    }
  }

  private def checkPassKey = {
    if (req.getHeader(XML_ATTRIBUTE_USER_ID) != null && req.getHeader(XML_ATTRIBUTE_PASS_KEY) != null) {
      val userId = req.getHeader(XML_ATTRIBUTE_USER_ID)
      val passKey = req.getHeader(XML_ATTRIBUTE_PASS_KEY)
      val userKey = KeyFactory.createKey(KIND_USER_DETAILS, userId)
      try {
        val userEntity = datastore.get(userKey)
        val dsUserID = userEntity.getProperty(COLUMN_USER_ID)
        val dsPassKey = userEntity.getProperty(COLUMN_PASS_KEY)
        if (dsUserID == userId && dsPassKey == passKey) {
          true
        } else {
          false
        }
      } catch {
        case _: IllegalArgumentException | _: EntityNotFoundException => false
      }
    } else {
      false
    }
  }

  def apiAuthentication(format: String, f: (LoggedIn) => Result) = {
    if (userPrincipal != null) {
      val currUserId = userPrincipal.getName
      f(new LoggedIn(currUserId, req))
    } else if (checkPassKey) {
      val currUserId = req.getHeader(XML_ATTRIBUTE_USER_ID)
      f(new LoggedIn(currUserId, req))
    } else {
      format match {
        case "xml" => XmlContent(ResponseStatus(false, "Invalid UserID or PassKey").mkXML, 400)
        case "json" => JsonContent(ResponseStatus(false, "Cannot Retrieve as the user does not exists!").mkJson, 400)
      }
    }
  }

}

object Helper {

  val KIND_USER_DETAILS = "userDetails"
  val COLUMN_USER_ID = "userID"
  val COLUMN_PASS_KEY = "passKey"
  val COLUMN_SHARED_WITH = "sharedWith"
  val COLUMN_VERSION_NO = "versionNo"
  val KIND_SESSIONS = "sessions"
  val COLUMN_SESSION_ID = "sessionID"
  val KIND_BATCHES = "batches"
  val COLUMN_BATCH_ID = "batchID"
  val KIND_LOCATIONS = "locations"
  val COLUMN_LATITUDE = "latitude"
  val COLUMN_LONGITUDE = "longitude"
  val COLUMN_ACCURACY = "accuracy"
  val COLUMN_TIME_STAMP = "timestamp"
  val XML_TAG_BATCH = "batch"
  val XML_TAG_LOCATION = "loc"
  val XML_ATTRIBUTE_UPLOAD_ID = "uid"
  val XML_ATTRIBUTE_SESSION_ID = "sid"
  val XML_ATTRIBUTE_BATCH_ID = "bid"
  val XML_ATTRIBUTE_USER_ID = "userid"
  val XML_ATTRIBUTE_PASS_KEY = "passkey"
  val XML_ATTRIBUTE_LATITUDE = "lat"
  val XML_ATTRIBUTE_LONGITUDE = "lng"
  val XML_ATTRIBUTE_ACCURACY = "acc"
  val XML_ATTRIBUTE_TIME_STAMP = "ts"
  val PARAM_VERSION_NO = "versionNo";
  val PARAM_SHARE_WITH = "shareWith"
  val PARAM_PASS_KEY = "passKey"
  val FILE_NOT_FOUND = <p>File Not Found</p>
  val GRACE_PERIOD = 3600000
  val LOCATIONS_LIMIT = 1000
  val datastore = DatastoreServiceFactory.getDatastoreService

  def userExistsFunc(userId: String) = {
    try {
      val userKey = KeyFactory.createKey(KIND_USER_DETAILS, userId)
      datastore.get(userKey)
      true
    } catch {
      case _: IllegalArgumentException | _: EntityNotFoundException => false
    }
  }

  def sessionExistsFunc(userId: String, sessionId: String) = {
    try {
      val sessionKey = mkSessionKey(mkUserKey(userId), sessionId)
      datastore.get(sessionKey)
      true
    } catch {
      case _: IllegalArgumentException | _: EntityNotFoundException => false
    }
  }

  def batchExistsFunc(userId: String, sessionId: String, batchId: Int) = {
    try {
      val batchKey = mkBatchKey(mkSessionKey(mkUserKey(userId), sessionId), batchId)
      datastore.get(batchKey)
      true
    } catch {
      case _: IllegalArgumentException | _: EntityNotFoundException => false
    }
  }

  def getUserEntity(userId: String) = {
    datastore.get(mkUserKey(userId))
  }

  def mkUserKey(userId: String) = {
    KeyFactory.createKey(KIND_USER_DETAILS, userId)
  }

  def mkSessionKey(userKey: Key, sessionId: String) = {
    KeyFactory.createKey(userKey, KIND_SESSIONS, sessionId)
  }

  def mkBatchKey(sessionKey: Key, batchId: Int) = {
    KeyFactory.createKey(sessionKey, KIND_BATCHES, batchId)
  }

  def createTemplate(menu: xml.Node, userName: String, body: xml.Node, jScript: Option[String] = None, logoutURL: String) = {
    <html lang="en">
      <head>
        <script src="/static/js/jquery-1.9.1.min.js"></script>
        <script src="/static/js/bootstrap.min.js"></script>
        <script src="/static/js/moment.min.js"></script>
        <link rel="stylesheet" href="/static/style/style.css" type="text/css"></link>
        <link href="/static/style/bootstrap.min.css" rel="stylesheet" media="screen"></link>
        {
          jScript.map { script =>
            <script>{ xml.Unparsed(script) }</script>
          }.getOrElse(xml.Null)
        }
        <script src="/static/js/OpenLayers.js"></script>
        <script src="/static/js/locationsDisplay.js"></script>
      </head>
      <body>
        <div class="container-fluid">
          <div class="row-fluid">
            <div class="span2">
              <h3>TrackMe</h3>
              <i class="icon-user"></i>{ userName }
              { menu }
            </div>
            { body }
          </div>
        </div>
      </body>
    </html>
  }

}

