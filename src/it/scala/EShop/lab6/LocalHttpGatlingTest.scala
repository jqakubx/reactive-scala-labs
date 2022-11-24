package EShop.lab6

import io.gatling.core.Predef.{jsonFile, rampUsers, scenario, Simulation, StringBody, _}
import io.gatling.http.Predef.http

import java.nio.file.Paths
import scala.concurrent.duration._

class LocalHttpGatlingTest extends Simulation {

  val httpProtocol = http //values here are adjusted to cluster_demo.sh script
    .baseUrls("http://localhost:9000")
//    .baseUrls("http://localhost:9001", "http://localhost:9002", "http://localhost:9003")
    .acceptHeader("text/plain,text/html,application/json,application/xml;")
    .userAgentHeader("Mozilla/5.0 (Windows NT 5.1; rv:31.0) Gecko/20100101 Firefox/31.0")

  val scn = scenario("BasicSimulation")
    .feed(
      jsonFile(Paths.get(classOf[LocalHttpGatlingTest].getResource("/data/search_data.json").toURI).toString).random
    )
    .exec(
      http("products")
        .get(""" /products?brand="${brand}&keywords="${keywords}" """.strip())
        .asJson
    )
    .pause(5)

  setUp(
    scn.inject(
      rampUsers(100)
        .during(15.seconds)
    )
  ).protocols(httpProtocol)
}
