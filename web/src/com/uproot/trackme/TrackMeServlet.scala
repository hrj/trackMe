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

  override def doGet(req: HttpServletRequest, resp: HttpServletResponse) {

    val common = new CommonFunctions(req)

    val result = common.requestPath match {
      case Nil => Redirect("/web/home")
      case "web" :: page :: Nil => {
        common.webAuthentication { userId: String =>
          page match {
            case "home" => common.homePage(userId)
            case "settings" => common.settingsPage
            case _ => common.fileNotFound
          }
        }
      }
      case "api" :: format :: operation :: Nil => {
        common.apiAuthentication(format, {
          operation match {
            case "retrieve" => common.retrieveLocations
            case _ => common.fileNotFound
          }
        })
      }
      case _ => common.fileNotFound
    }

    common.sendResponse(result, resp)
  }

  override def doPost(req: HttpServletRequest, resp: HttpServletResponse) = {

    val common = new CommonFunctions(req)

    val result = common.requestPath match {
      case "web" :: page :: Nil => {
        common.webAuthentication { userId: String =>
          page match {
            case "settings" => common.updateSettings
            case _ => common.fileNotFound
          }
        }
      }
      case "api" :: format :: operation :: Nil => {
        common.apiAuthentication(format, {
          operation match {
            case "store" => common.storeLocations
            case _ => common.fileNotFound
          }
        })
      }
      case _ => common.fileNotFound
    }

    common.sendResponse(result, resp)
  }
}