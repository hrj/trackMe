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

class TrackMeServlet extends HttpServlet {

  private def sendResponse(result: Result, resp: HttpServletResponse) {
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

  override def doGet(req: HttpServletRequest, resp: HttpServletResponse) {

    val common = new CommonFunctions(req)

    val result = common.requestPath match {
      case Nil => Redirect("/web/home")
      case "web" :: page => {
        common.webAuthentication { loggedIn =>
          page match {
            case "home" :: Nil=> loggedIn.homePage()
            case "settings" :: Nil => loggedIn.settingsPage()
            case "getuserlocations" :: userId :: Nil => loggedIn.getUserLocations(userId)
            case "user" :: userId :: Nil => loggedIn.viewLocations(userId)
            case _ => common.fileNotFound
          }
        }
      }
      case "api" :: "v1" :: format :: operation :: Nil => {
        common.apiAuthentication(format, { loggedIn =>
          operation match {
            case "retrieve" => loggedIn.retrieveLocations
            case "validate" => loggedIn.validate
            case _ => common.fileNotFound
          }
        })
      }
      case _ => common.fileNotFound
    }

    sendResponse(result, resp)
  }

  override def doPost(req: HttpServletRequest, resp: HttpServletResponse) = {

    val common = new CommonFunctions(req)

    val result = common.requestPath match {
      case "web" :: page :: Nil => {
        common.webAuthentication { loggedIn =>
          page match {
            case "settings" => loggedIn.updateSettings
            case _ => common.fileNotFound
          }
        }
      }
      case "api" :: "v1" :: format :: operation :: Nil => {
        common.apiAuthentication(format, { loggedIn =>
          operation match {
            case "store" => loggedIn.storeLocations
            case _ => common.fileNotFound
          }
        })
      }
      case _ => common.fileNotFound
    }

    sendResponse(result, resp)
  }
}