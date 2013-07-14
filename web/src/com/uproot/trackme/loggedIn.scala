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

import Helper.COLUMN_ACCURACY
import Helper.COLUMN_BATCH_ID
import Helper.COLUMN_LATITUDE
import Helper.COLUMN_LONGITUDE
import Helper.COLUMN_PASS_KEY
import Helper.COLUMN_SESSION_ID
import Helper.COLUMN_SHARED_WITH
import Helper.COLUMN_TIME_STAMP
import Helper.COLUMN_USER_ID
import Helper.COLUMN_VERSION_NO
import Helper.GRACE_PERIOD
import Helper.KIND_LOCATIONS
import Helper.KIND_USER_DETAILS
import Helper.LOCATIONS_LIMIT
import Helper.datastore
import Helper.getUserEntity
import Helper.mkBatchKey
import Helper.mkSessionKey
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
    val updateVersionNo = req.getParameter(PARAM_VERSION_NO).toLong
    val passKey = req.getParameter(PARAM_PASS_KEY)
    val shareWith = req.getParameter(PARAM_SHARE_WITH)
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
        userEntity.setProperty(COLUMN_USER_ID, currUserId)
        userEntity.setProperty(COLUMN_VERSION_NO, serverVersionNo + 1L)
        userEntity.setProperty(COLUMN_PASS_KEY, passKey)
        if (validUsers.nonEmpty) {
          userEntity.setProperty(COLUMN_SHARED_WITH, (validUsers).asJava)
        } else {
          userEntity.removeProperty(COLUMN_SHARED_WITH)
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
        <input type="hidden" name={ PARAM_VERSION_NO } value={ versionNo.toString }/>
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
            <input id="passKey" type="text" name={ PARAM_PASS_KEY } value={ passKey }/>
          </div>
        </div>
        <div class="control-group">
          <label class="control-label" for="shareWith">
            Share With
          </label>
          <div class="controls">
            <input id="shareWith" type="text" name={ PARAM_SHARE_WITH } value={ sharedWith }/>
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
        val userKey = KeyFactory.createKey(KIND_USER_DETAILS, currUserId)
        val userEntity = datastore.get(userKey)
        val passKey = userEntity.getProperty(COLUMN_PASS_KEY).toString
        val versionNo = userEntity.getProperty(COLUMN_VERSION_NO).asInstanceOf[Long]
        val shared = if (userEntity.hasProperty(COLUMN_SHARED_WITH)) {
          val sharedWith = userEntity.getProperty(COLUMN_SHARED_WITH)
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
    val userEntity = datastore.get(KeyFactory.createKey(KIND_USER_DETAILS, userId))
    if (userEntity.hasProperty(COLUMN_SHARED_WITH)) {
      val shared = userEntity.getProperty(COLUMN_SHARED_WITH).asInstanceOf[ArrayList[String]]
      shared.asScala.toSeq
    } else {
      Nil
    }
  }

  private def sharedFrom(userId: String) = {
    val shareFromFilter = new FilterPredicate(COLUMN_SHARED_WITH, FilterOperator.EQUAL, userId)
    val q = new Query(KIND_USER_DETAILS).setFilter(shareFromFilter)
    val usersSharedFrom = datastore.prepare(q).asIterable.asScala.toSeq
    usersSharedFrom.map(_.getProperty(COLUMN_USER_ID).asInstanceOf[String])
  }

  private def getSharingDetails(userId: String) = {
    <div class="span3">
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
        xml.Group(Seq(<div class="span7">
                        { mapElem }{ refreshButton }
                      </div>,
          getSharingDetails(currUserId))), Some("var retrieveURL = \"/api/v1/json/retrieve\";" + "var curUser = \"" + currUserId + "\";")))

    } else {
      Redirect("/web/settings")
    }
  }

  val isDevServer = "yes" equals System.getProperty("devServer")

  def storeLocations() = {
    val inputStream = req.getHeader("Content-Encoding") match {
      case "gzip" if (isDevServer) => new GZIPInputStream(req.getInputStream)
      case _ => req.getInputStream
    }

    val uploadDetails = xml.XML.load(inputStream)
    val upload = new Upload(uploadDetails)
    val batches = upload.batches
    val maxTime = System.currentTimeMillis + GRACE_PERIOD
    if (batches.length > 0) {
      val result = batches.map { batch =>
        val userid = upload.userid
        val sid = batch.sid
        val bid = batch.bid
        val locations = batch.locations
        if (locations.forall(_.isValid(maxTime))) {
          val userKey = mkUserKey(userid)
          val sessionKey = mkSessionKey(userKey, sid)
          val batchKey = mkBatchKey(sessionKey, bid)

          val sessionEntity = new Entity(KIND_SESSIONS, sessionKey)
          sessionEntity.setProperty(COLUMN_SESSION_ID, sid)
          datastore.put(sessionEntity)

          val batchEntity = new Entity(KIND_BATCHES, batchKey)
          batchEntity.setProperty(COLUMN_BATCH_ID, bid)
          datastore.put(batchEntity)

          val locationEntities: List[Entity] = batch.locations.map { loc =>
            val batchLocations = new Entity(KIND_LOCATIONS, batchKey)
            batchLocations.setProperty(COLUMN_LATITUDE, loc.latLong.latitude)
            batchLocations.setProperty(COLUMN_LONGITUDE, loc.latLong.longitude)
            batchLocations.setProperty(COLUMN_ACCURACY, loc.accuracy)
            batchLocations.setProperty(COLUMN_TIME_STAMP, loc.timeStamp)
            batchLocations
          }
          datastore.put(locationEntities.asJava)
          (sid, bid, true)
        } else {
          (sid, bid, false)
        }
      }

      val response = <upload uid={ upload.uploadid.toString }>
                       {
                         result.map { t =>
                           <batch sid={ t._1 } bid={ t._2.toString } accepted={ t._3.toString }/>
                         }
                       }
                     </upload>

      XmlContent(response)

    } else XmlContent(ResponseStatus(false, "No Locations to upload").mkXML, 400)
  }

  private def quote(value: String) = "\"" + value + "\""

  private def sharedLocationsMkJson(sharedLocations: Seq[(String, Location)]) = {
    "\"sharedLocations\":" + sharedLocations.map { user =>
      quote(user._1) + ":" + user._2.mkJSON
    }.mkString("{", ",", "}")
  }

  private def getLastLocations(userId: String) = {
    sharedFrom(userId).flatMap { sharerId =>
      val userKey = mkUserKey(sharerId)
      val userQuery = new Query(KIND_LOCATIONS).setAncestor(userKey) addSort (COLUMN_TIME_STAMP, SortDirection.DESCENDING)
      val usersLastLocation = ((datastore.prepare(userQuery)).asList(FetchOptions.Builder.withLimit(1))).asScala.headOption
      usersLastLocation.map { location =>
        val latLong = LatLong(location.getProperty(COLUMN_LATITUDE).asInstanceOf[Double], location.getProperty(COLUMN_LONGITUDE).asInstanceOf[Double])
        val timeStamp = location.getProperty(COLUMN_TIME_STAMP).asInstanceOf[Long]
        val accuracy = location.getProperty(COLUMN_ACCURACY).asInstanceOf[Long]
        (sharerId, Location(latLong, accuracy, timeStamp))
      }
    }
  }

  private def getLocations(userId: String) = {
    val userKey = mkUserKey(userId)
    val query = new Query(KIND_LOCATIONS).setAncestor(userKey) addSort (COLUMN_TIME_STAMP, SortDirection.DESCENDING)
    val locations = datastore.prepare(query).asList(FetchOptions.Builder.withLimit(LOCATIONS_LIMIT)).asScala
    println(locations.length)
    (locations.map { location =>
      val latLong = LatLong(location.getProperty(COLUMN_LATITUDE).asInstanceOf[Double], location.getProperty(COLUMN_LONGITUDE).asInstanceOf[Double])
      val timeStamp = location.getProperty(COLUMN_TIME_STAMP).asInstanceOf[Long]
      val accuracy = location.getProperty(COLUMN_ACCURACY).asInstanceOf[Long]
      (Location(latLong, accuracy, timeStamp)).mkJSON
    }).mkString("\"locations\":[", ",", "]")
  }

  def getUserLocations(userId: String) = {
    if ((userId == currUserId || (sharedFrom(currUserId).contains(userId) && userExistsFunc(userId)))) {
      JsonContent("{" + getLocations(userId) + "}")
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

  def validate = {
    XmlContent(ResponseStatus(true, "User Authenticated").mkXML)
  }

  def viewLocations(userId: String) = {
    if (userExists) {
      if (sharedFrom(currUserId).contains(userId) || userId == currUserId) {
        val url = "var retrieveURL = \"/web/getuserlocations/" + userId + "\";" + "var curUser = \"" + userId + "\";"
        XmlContent(createTemplate("",
          xml.Group(Seq(
            <div class="span7">
              <h3>Showing Map for : { userId }</h3>
              { mapElem }{ refreshButton }
            </div>,
            <div class="span3">
              <h4>Shared From</h4>
              <ul class="nav nav-list">{ mkXmlLinkList(sharedFrom(currUserId), Some("No Shares!")) }</ul>
            </div>)), Some(url)))
      } else {
        XmlContent(createTemplate("", <b>The user does not share his locations with you!</b>), 400)
      }
    } else {
      Redirect("/web/home")
    }
  }

}
