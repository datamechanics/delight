package co.datamechanics.delight.common

import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.util.EntityUtils
import org.apache.spark.internal.Logging
import org.json4s.DefaultFormats
import org.json4s.JsonAST.JValue
import org.json4s.jackson.JsonMethods.{compact, parse, render}

import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import scala.util.Try

object Network extends Logging {
  implicit val formats: DefaultFormats.type = org.json4s.DefaultFormats
  private val isRateLimited: AtomicBoolean = new AtomicBoolean(false)


  /**
    * Parse an optional return message from the API
    */
  private def parseApiReturnMessage(httpResponse: HttpResponse): Option[String] = {
    Try {
      val entity = httpResponse.getEntity
      val body = EntityUtils.toString(entity)
      (parse(body) \\ "message").extract[Option[String]]
    }.toOption.flatten
  }

  /**
    * Send a POST request to Data Mechanics collector API ("the server")
    *
    * - Handles access token
    * - Status Code:
    *   -> 200: Request is a success
    *   -> 429: RateLimit has been reached for this app
    *   -> Other: Throw IOException
    *
    */
  def sendRequest(client: HttpClient, url: String, accessToken: String, payload: JValue, successMsg: String): Unit = {
    if (isRateLimited.get) return

    val payloadAsString = compact(render(payload))
    val requestEntity = new StringEntity(payloadAsString)

    val postMethod = new HttpPost(url)
    postMethod.setHeader("X-API-key", accessToken)
    postMethod.setEntity(requestEntity)

    val httpResponse: HttpResponse = client.execute(postMethod)

    val apiReturnMessage: Option[String] = parseApiReturnMessage(httpResponse)
    val statusCode = httpResponse.getStatusLine.getStatusCode

    val entity = httpResponse.getEntity
    EntityUtils.consume(entity)

    statusCode match {
      case 200 => logInfo(successMsg)
      case 429 =>
        isRateLimited.set(true)
        logError(s"RateLimit has been reached, collection will now stop")
      case _ =>
        var errorMessage = s"Status $statusCode: ${httpResponse.getStatusLine.getReasonPhrase}."
        apiReturnMessage.foreach(
          m => errorMessage += s" $m."
        )
        throw new IOException(errorMessage)
    }
  }
}
