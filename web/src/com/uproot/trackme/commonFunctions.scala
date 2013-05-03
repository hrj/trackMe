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
    if (req.getHeader(USER_ID) != null && req.getHeader(PASS_KEY) != null) {
      val userId = req.getHeader(USER_ID)
      val passKey = req.getHeader(PASS_KEY)
      val userKey = KeyFactory.createKey(USER_DETAILS, userId)
      try {
        val userEntity = datastore.get(userKey)
        val dsUserID = userEntity.getProperty("userID")
        val dsPassKey = userEntity.getProperty("passKey")
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
      val currUserId = req.getHeader(USER_ID)
      f(new LoggedIn(currUserId, req))
    } else {
      format match {
        case "xml" => XmlContent(ResponseStatus(false, "Invalid UserID or PassKey").mkXML)
        case "json" => JsonContent(ResponseStatus(false, "Cannot Retrieve as the user does not exists!").mkJson)
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
    val LOCATIONS_LIMIT = 100
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

  def createTemplate(menu: xml.Node, userName: String, body: xml.Node, jScript: Option[String] = None, logoutURL: String) = {
    <html lang="en">
      <head>
        <script src="/static/js/jquery-1.9.1.min.js"></script>
        <script src="/static/js/bootstrap.min.js"></script>
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
              <i class="icon-user"></i> { userName }
              { menu }
            </div>
            { body }
          </div>
        </div>
      </body>
    </html>
  }

}

