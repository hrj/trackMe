package com.uproot.trackme;

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

class CommonFunctions(req: HttpServletRequest) {

  val USER_ID = "userID";
  val USER_DETAILS = "userDetails";
  val PASS_KEY = "passKey";
  val userService = UserServiceFactory.getUserService();
  val thisURL = req.getRequestURI();
  val userPrincipal = req.getUserPrincipal();
  val datastore = DatastoreServiceFactory.getDatastoreService();

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
    val passKey = req.getParameter("passKey1")
    val trackMeKey = KeyFactory.createKey(USER_DETAILS,
      "testtrackme");
    val userDetails = new Entity(USER_DETAILS, trackMeKey);
    userDetails.setProperty(USER_ID, req.getUserPrincipal().getName());
    userDetails.setProperty("passKey", passKey);
    datastore.put(userDetails);
    settingsPage
  }

  def settingsPage() = {
    val userId = req.getUserPrincipal().getName()
    if (userExists(userId)) {
      val userExists = new FilterPredicate(USER_ID,
        FilterOperator.EQUAL, userId);
      val query = new Query(USER_DETAILS).setFilter(userExists);
      val pq = datastore.prepare(query).asSingleEntity();
      val passKey = pq.getProperty("passKey").toString
      XmlContent(createTemplate(xml.Group(Seq(<label>UserId : <input type="text" value={ userId }></input></label>,
        <label>PassKey : <input type="text" value={ passKey }></input></label>))))
    } else {
      XmlContent(createTemplate(
        <form action="/web/settings" method="post">
          <label>UserId : <input type="text" value={ userId }/></label>
          <label>Pass Key : <input type="text" name="passKey1"/></label><br/>
          <input type="submit"/>
        </form>))
    }

  }

  def webAuthentication(f: (String) => Result) = {
    if (userPrincipal != null) {
      val userId = userPrincipal.getName();
      f(userId)
    } else {
      XmlContent(<p>Please <a href={ userService.createLoginURL(thisURL) }>sign in</a></p>)
    }
  }

  def userExists(userId: String) = {
    val userExists = new FilterPredicate(USER_ID,
      FilterOperator.EQUAL, userId);
    val query = new Query(USER_DETAILS).setFilter(userExists);
    val pq = datastore.prepare(query).asSingleEntity();

    pq != null
  }

  def homePage(userId: String) = {
    if (userExists(userId)) {
      XmlContent(createTemplate(
        xml.Group(Seq(<div id="map" class="bigmap"></div>,
          <input type="button" value="Refresh" id="mapUpdate"></input>))))

    } else {
      Redirect("/web/settings")
    }
  }

  def storeLocations() = {
    val inputStream = req.getHeader("Content-Encoding") match {
      case "gzip" => new GZIPInputStream(req.getInputStream())
      case _ => req.getInputStream()
    }

    println(req.getContentLength())
    val sessionDetails = xml.XML.load(inputStream)
    println((sessionDetails \ "location" take 5).mkString("\n"))
    val sessionDet = new Session(sessionDetails)

    val userExistsFilter = new FilterPredicate(USER_ID,
      FilterOperator.EQUAL, sessionDet.userID);

    val passKeyCheckFilter = new FilterPredicate(PASS_KEY,
      FilterOperator.EQUAL, sessionDet.passKey);

    val userVerificationFilter = CompositeFilterOperator.and(userExistsFilter, passKeyCheckFilter);

    val query = new Query(USER_DETAILS).setFilter(userVerificationFilter);

    val pq = datastore.prepare(query).asSingleEntity();
    if (pq == null) {
      println("User Does not exists or wrong passKey")
      XmlContent(<p>User Does no exist or wrong PassKey</p>)
    } else {
      val sessionKey = KeyFactory.createKey(USER_ID, "trackMe");
      sessionDet.locationDetails.foreach { loc =>
        val userID = new Entity(sessionDet.userID, sessionKey);
        userID.setProperty("latitude", loc.latitude.toDouble);
        userID.setProperty("longitude", loc.longitude.toDouble);
        userID.setProperty("accuracy", loc.accuracy);
        userID.setProperty("timeStamp", loc.timeStamp);
        datastore.put(userID)
      };
      println("Location added successfuly")
      XmlContent(<p>Location added successfuly</p>)
    }
  }

  def retrieveLocations() = {
    val userId = req.getUserPrincipal().getName()
    val userFilter = new FilterPredicate("userID", FilterOperator.EQUAL, userId);
    val userQuery = new Query(USER_DETAILS).setFilter(userFilter);
    val userExists = datastore.prepare(userQuery).asSingleEntity()
    if (userExists != null) {
      val query = new Query(userId)
      val pq = datastore.prepare(query).asIterable().asScala;
      val locations = pq.map { location =>
        LatLong(location.getProperty("longitude").toString().toDouble,
          location.getProperty("latitude").toString().toDouble)
      }
      JsonContent("{\"locations\":[" + (locations.map(_.mkJSON())).mkString(",") + "]}")
    } else {
      JsonContent("""{errorMessage:"Cannot Retrieve since the user does not Exists!"}""")
    }
  }

  def sendResponse(result: Result, resp: HttpServletResponse) {
    result match {
      case c: Content => {
        resp.setContentType(c.contentType);
        resp.getWriter().println(c.content)
      }
      case Redirect(url) => {
        resp.sendRedirect((url).toString())
      }
    }
  }

}
