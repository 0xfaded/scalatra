package org.scalatra
package json

import java.io.InputStream
import org.json4s._
import Xml._
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import org.slf4j.LoggerFactory

object JsonSupport {

  val ParsedBodyKey = "org.scalatra.json.ParsedBody"
}

trait JsonSupport[T] extends JsonOutput[T] {

  import JsonSupport._

  private[this] val logger = LoggerFactory.getLogger(getClass)

  private[this] val _defaultCacheRequestBody = true
  protected def cacheRequestBodyAsString: Boolean = _defaultCacheRequestBody
  protected def parseRequestBody(format: String)(implicit request: HttpServletRequest) = try {
    val ct = request.contentType getOrElse ""
    if (format == "json") {
      val bd  = {
        if (ct == "application/x-www-form-urlencoded") multiParams.keys.headOption map readJsonFromBody getOrElse JNothing
        else if (cacheRequestBodyAsString) readJsonFromBody(request.body)
        else readJsonFromStream(request.inputStream)
      }
      transformRequestBody(bd)
    } else if (format == "xml") {
      val bd = {
        if (ct == "application/x-www-form-urlencoded") multiParams.keys.headOption map readXmlFromBody getOrElse JNothing
        else if (cacheRequestBodyAsString) readXmlFromBody(request.body)
        else readXmlFromStream(request.inputStream)
      }
      transformRequestBody(bd)
    } else JNothing
  } catch {
    case t: Throwable ⇒ {
      logger.error(s"Parsing the request body failed, because:", t)
      JNothing
    }
  }

  protected def readJsonFromBody(bd: String): JValue
  protected def readJsonFromStream(stream: InputStream): JValue
  protected def readXmlFromBody(bd: String): JValue = {
    val JObject(JField(_, jv) :: Nil) = toJson(scala.xml.XML.loadString(bd))
    jv
  }
  protected def readXmlFromStream(stream: InputStream): JValue = {
    val JObject(JField(_, jv) :: Nil) = toJson(scala.xml.XML.load(stream))
    jv
  }
  protected def transformRequestBody(body: JValue) = body

  override protected def invoke(req: HttpServletRequest, resp: HttpServletResponse, matchedRoute: MatchedRoute) = {
    withRouteMultiParams(req, Some(matchedRoute)) {
      val mt = req.contentType map {
        _.split(";").head
      } getOrElse "application/x-www-form-urlencoded"
      val fmt = mimeTypes get mt getOrElse "html"
      if (shouldParseBody(fmt)(req)) {
        req(ParsedBodyKey) = parseRequestBody(fmt)(req).asInstanceOf[AnyRef]
      }
      super.invoke(req, resp, matchedRoute)
    }
  }

  protected def shouldParseBody(fmt: String)(implicit request: HttpServletRequest) =
    (fmt == "json" || fmt == "xml") && parsedBody == JNothing

  def parsedBody(implicit request: HttpServletRequest): JValue = request.get(ParsedBodyKey).map(_.asInstanceOf[JValue]) getOrElse {
    val fmt = format
    var bd: JValue = JNothing
    if (fmt == "json" || fmt == "xml") {
      bd = parseRequestBody(fmt)
      request(ParsedBodyKey) = bd.asInstanceOf[AnyRef]
    }
    bd
  }
}
