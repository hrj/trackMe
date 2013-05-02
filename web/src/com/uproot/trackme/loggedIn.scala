package com.uproot.trackme;

import java.util.ArrayList
import java.util.ConcurrentModificationException
import java.util.zip.GZIPInputStream

import scala.Option.option2Iterable
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.collection.JavaConverters.seqAsJavaListConverter

import com.google.appengine.api.datastore.Entity
import com.google.appengine.api.datastore.FetchOptions
import com.google.appengine.api.datastore.KeyFactory
import com.google.appengine.api.datastore.Query
import com.google.appengine.api.datastore.Query.FilterOperator
import com.google.appengine.api.datastore.Query.FilterPredicate
import com.google.appengine.api.datastore.Query.SortDirection
import com.google.appengine.api.users.UserServiceFactory

import Helper.GRACE_PERIOD
import Helper.LOCATIONS
import Helper.PASS_KEY
import Helper.SHARED_WITH
import Helper.USER_DETAILS
import Helper.USER_ID
import Helper.VERSION_NO
import Helper.datastore
import Helper.getUserEntity
import Helper.mkUserKey
import Helper.userExistsFunc
import javax.servlet.http.HttpServletRequest

class LoggedIn(currUserId: String, req: HttpServletRequest) {

  import Helper._
  private def userExists = userExistsFunc(currUserId)
  private val userService = UserServiceFactory.getUserService
  private val thisURL = req.getRequestURI
  private val logoutURL = userService.createLogoutURL(thisURL)
  private val menu = new Menu(
    Seq(MenuEntry("Home", "icon-home", "/web/home"),
      MenuEntry("Settings", "icon-wrench", "/web/settings"),
      MenuEntry("Logout", "icon-share", logoutURL)))

  private def createTemplate(activeTitle: String, message: xml.Node, jScript: Option[String] = None) = {
    Helper.createTemplate(menu.createMenu(activeTitle), currUserId, message, jScript, logoutURL = logoutURL)
  }

  private def mkTxn[T](f: => T) = {
    var retry = 10000
    var result: Option[T] = None
    while (retry > 0) {
      val tx = datastore.beginTransaction
      try {
        result = Some(f)
        tx.commit
      } catch {
        case e: ConcurrentModificationException =>
          retry -= 1
      } finally {
        if (tx.isActive) {
          tx.rollback
          println("rollBack")
        } else {
          retry = 0
        }
      }
    }
    result.get
  }

  private def mkAlert(message: String, alertType: Option[String]) = {
    <div class={ (Seq("alert") ++ alertType.map("alert-" + _)).mkString(" ") }>
      <button type="button" class="close" data-dismiss="alert">&times;</button>
      <strong>{ alertType.map { _ + "! " }.getOrElse("") }</strong>{ message }
    </div>
  }

  def updateSettings() = {
    val updateVersionNo = req.getParameter("versionNo").toLong
    val passKey = req.getParameter("passKey")
    val shareWith = req.getParameter("shareWith")
    val usersSharingWith = shareWith.split(",").toList.filter(_.length != 0)
    val (validUsers, invalidUsers) = usersSharingWith.partition(userExistsFunc(_))
    val userKey = mkUserKey(currUserId)
    val (message, alertType) = mkTxn {
      val (userEntity, serverVersionNo) = if (userExists) {
        val userEntity = getUserEntity(currUserId)
        (userEntity, userEntity.getProperty("versionNo").asInstanceOf[Long])
      } else {
        (new Entity(userKey), 0L)
      }
      if (serverVersionNo == updateVersionNo) {
        userEntity.setProperty(VERSION_NO, serverVersionNo + 1L)
        userEntity.setProperty(PASS_KEY, passKey)
        if (validUsers.nonEmpty) {
          userEntity.setProperty(SHARED_WITH, (validUsers).asJava)
        } else {
          userEntity.removeProperty(SHARED_WITH)
        }
        datastore.put(userEntity)
        if (invalidUsers.nonEmpty) {
          ("Some settings updated, failed to share with the folling Users as they are not registered Users: " +
            invalidUsers.mkString(" , "), None)
        } else {
          ("Settings Updated Successfully!", Some("success"))
        }
      } else {
        ("Update Failed! Attempt to update outdated information.", Some("error"))
      }
    }
    settingsPage(Some(mkAlert(message, alertType)))
  }

  private def userSettingsForm(passKey: String, sharedWith: String, message: Option[xml.Node], versionNo: Long) =
    <form class="form-horizontal" action="/web/settings" method="post">
      <fieldset>
        {
          message.map { alert =>
            alert
          }.getOrElse(xml.Null)
        }
        <input type="hidden" name="versionNo" value={ versionNo.toString }/>
        <div class="control-group">
          <label class="control-label" for="userId">UserId </label>
          <div class="controls">
            <span id="userId" class="uneditable-input">{ currUserId }</span>
          </div>
        </div>
        <div class="control-group">
          <label class="control-label" for="passKey">
            Pass Key
          </label>
          <div class="controls">
            <input id="passKey" type="text" name="passKey" value={ passKey }/>
          </div>
        </div>
        <div class="control-group">
          <label class="control-label" for="shareWith">
            Share With
          </label>
          <div class="controls">
            <input id="shareWith" type="text" name="shareWith" value={ sharedWith }/>
          </div>
        </div>
        <div class="control-group">
          <div class="controls">
            <button type="submit" class="btn btn-primary">Submit</button>
          </div>
        </div>
      </fieldset>
    </form>

  def settingsPage(message: Option[xml.Node] = None) = {
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
        (userSettingsForm("", "", Some(mkAlert("Please Set your PassKey", None)), 0))
      }
    XmlContent(createTemplate("Settings", settingsForm))
  }

  private def mkXmlLinkList(listElements: Seq[String], message: Option[String] = None) = {
    if (listElements.nonEmpty) {
      listElements.map(element => <li><a href={ "/web/user/" + element }><i class="icon-map-marker"></i> { element }</a></li>)
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
    <div class="span2">
      <h4>Shared With</h4>
      <ul>{ mkXmlList(sharedWith(userId), Some("No Shares!")) }</ul>
      <h4>Shared From</h4>
      <ul class="nav nav-list">{ mkXmlLinkList(sharedFrom(userId), Some("No Shares!")) }</ul>
    </div>
  }
  val mapElem =
    <div id="map-wrapper">
      <div id="map" class="bigmap"></div>
    </div>
  val refreshButton =
    <input type="button" value="Refresh" id="mapUpdate" class="btn btn-inverse"></input>
  def homePage() = {
    if (userExists) {
      XmlContent(createTemplate("Home",
        xml.Group(Seq(<div class="span8">
                        { mapElem }{ refreshButton }
                      </div>,
          getSharingDetails(currUserId))), Some("var retrieveURL = \"/api/json/retrieve\"")))

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
    val locations = sessionDet.locationDetails
    val maxTime = System.currentTimeMillis + GRACE_PERIOD
    if (locations.forall(_.isValid(maxTime))) {
      val userKey = mkUserKey(sessionDet.userId)
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
      val userKey = mkUserKey(sharerId)
      val userQuery = new Query(LOCATIONS).setAncestor(userKey) addSort ("timeStamp", SortDirection.DESCENDING)
      val usersLastLocation = ((datastore.prepare(userQuery)).asList(FetchOptions.Builder.withLimit(1))).asScala.headOption
      usersLastLocation.map { location =>
        (sharerId, LatLong(location.getProperty("latitude").asInstanceOf[Double], location.getProperty("longitude").asInstanceOf[Double]))
      }
    }
  }

  private def getLocations(userId: String) = {
    val userKey = mkUserKey(userId)
    val query = new Query(LOCATIONS).setAncestor(userKey)
    val locations = datastore.prepare(query).asList(FetchOptions.Builder.withLimit(10)).asScala
    (locations.map { location =>
      (LatLong(location.getProperty("latitude").asInstanceOf[Double], location.getProperty("longitude").asInstanceOf[Double])).mkJSON
    }).mkString("\"locations\":[", ",", "]")
  }

  def getUserLocations(userId: String) = {
    if ((userId == currUserId || (sharedFrom(currUserId).contains(userId) && userExistsFunc(userId)))) {
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
        XmlContent(createTemplate("",
          xml.Group(Seq(
            <div class="span8">
              <h3>Showing Map for : { userId }</h3>
              { mapElem }{ refreshButton }
            </div>,
            <div class="span2">
              <h4>Shared From</h4>
              <ul class="nav nav-list">{ mkXmlLinkList(sharedFrom(currUserId), Some("No Shares!")) }</ul>
            </div>)), Some(url)))
      } else {
        XmlContent(createTemplate("", <b>The user does not share his locations with you!</b>))
      }
    } else {
      Redirect("/web/home")
    }
  }

}