package com.uproot.trackme;

import java.util.ArrayList
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
  private val userExists = userExistsFunc(currUserId)
  private val userService = UserServiceFactory.getUserService
  private val thisURL = req.getRequestURI
  private val logoutURL = userService.createLogoutURL(thisURL)

  private def createTemplate(message: xml.Node, jScript: Option[String] = None) = {
    Helper.createTemplate(currUserId, message, jScript, logoutURL = logoutURL)
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
        XmlContent(createTemplate(
          xml.Group(Seq(<p><b>Showing Map for : { userId }</b></p>,
            <div id="map" class="bigmap"></div>,
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

}