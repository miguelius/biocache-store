package au.org.ala.biocache.load

import java.net.URI
import java.util
import java.util.{Date, UUID}
import java.util.concurrent.TimeUnit

import au.org.ala.biocache.cmd.Tool
import au.org.ala.biocache.model.Versions
import au.org.ala.biocache.util.OptionParser
import com.google.common.base.Optional
import com.google.common.primitives.Bytes
import org.apache.commons.httpclient.params.HttpConnectionParams
import org.apache.commons.lang.StringUtils
import org.apache.http.client.params.ClientPNames
import org.apache.http.conn.scheme.{PlainSocketFactory, Scheme, SchemeRegistry}
import org.apache.http.impl.client.{DecompressingHttpClient, DefaultHttpClient}
import org.apache.http.impl.conn.PoolingClientConnectionManager
import org.apache.http.params.BasicHttpParams
import org.gbif.crawler.{AbstractCrawlListener, CrawlConfiguration, CrawlContext, Crawler}
import org.gbif.crawler.client.HttpCrawlClient
import org.gbif.crawler.protocol.digir.{DigirCrawlConfiguration, DigirResponseHandler, DigirScientificNameRangeRequestHandler}
import org.gbif.crawler.retry.LimitedRetryPolicy
import org.gbif.crawler.strategy.{ScientificNameRangeCrawlContext, ScientificNameRangeStrategy}
import org.gbif.wrangler.lock.NoLockFactory
import org.slf4j.{LoggerFactory, MDC}

import scala.xml._

object DiGIRLoader extends Tool {

  def cmd = "load-DiGIR"
  def desc = "Load DiGIR."

  def main(args: Array[String]) {
    val LOG = LoggerFactory.getLogger(getClass)

    var dataResourceUid = ""
    var updateLastChecked = true
    var testFile = false
    var logRowKeys = false

    val parser = new OptionParser(help) {
      arg("data-resource-uid", "the data resource to import", { v: String => dataResourceUid = v })
      booleanOpt("u", "updateLastChecked", "update registry with last loaded date", { v: Boolean => updateLastChecked = v })
      opt("test", "test the file only do not load", { testFile = true })
      opt("log", "log row keys to file - allows processing/indexing of changed records", { logRowKeys = true })
    }

    if (parser.parse(args)) {
      val l = new DiGIRLoader
      l.load(dataResourceUid, testFile)
      try {
        if (updateLastChecked) {
          l.updateLastChecked(dataResourceUid)
        }
      } catch {
        case e: Exception => e.printStackTrace()
      } finally {
      }

    }
  }
}

class DiGIRLoader extends DataLoader {
  val LOG = LoggerFactory.getLogger(getClass)

  def load(dataResourceUid: String, test: Boolean) {
    val (protocol, urls, uniqueTerms, params, customParams, lastChecked) = retrieveConnectionParameters(dataResourceUid)
    val url = urls(0)

    //    val endpoint = new URI("http://84.204.46.10/digir/DiGIR.php")
    val endpoint = new URI(url)
    val code = params.get("resource").get //"ZIN_HerpNET"

    val gbifID = UUID.randomUUID() // not used
    val attempt = 1 // not used
    val manis = false // gbif specific, not used
    val config = new DigirCrawlConfiguration(gbifID, attempt, endpoint, code, manis)
    val context = new ScientificNameRangeCrawlContext()
    val strategy = new ScientificNameRangeStrategy(context)

    val retryPolicy = new LimitedRetryPolicy(5, 2, 5, 2)
    val requestHandler = new DigirScientificNameRangeRequestHandler(config)

    val client = HttpCrawlClientProvider.newHttpCrawlClient()

    val crawler = Crawler.newInstance(strategy, requestHandler, new DigirResponseHandler(), client, retryPolicy, NoLockFactory.getLock)

    val emit = (record: Map[String, String]) => {
      val fr = FullRecordMapper.createFullRecord("", record, Versions.RAW)
      if (!test) {
    	  if (load( dataResourceUid, fr, uniqueTerms)) {
    	    LOG.info(f"$dataResourceUid stored successfully")
    	  } else {
    		  LOG.info(f"Problems storing $dataResourceUid")
    	  }
      }
    }
    crawler.addListener(new AlaBiocacheListener(test, emit).asInstanceOf[org.gbif.crawler.CrawlListener[ScientificNameRangeCrawlContext, String, java.util.List[java.lang.Byte]]])
    crawler.addListener(new LoggingCrawlListener(config, null, null, 0, null).asInstanceOf[org.gbif.crawler.CrawlListener[ScientificNameRangeCrawlContext, String, java.util.List[java.lang.Byte]]])
    crawler.crawl()

    LOG.info("Done.")
  }
}

class AlaBiocacheListener(test: Boolean, emit: (Map[String, String]) => Unit) extends AbstractCrawlListener[ScientificNameRangeCrawlContext, String, java.util.List[java.lang.Byte]] {
  val LOG = LoggerFactory.getLogger(getClass)

  override def response(
    response: java.util.List[java.lang.Byte],
    retry: Int,
    duration: Long,
    recordCount: Optional[java.lang.Integer],
    endOfRecords: Optional[java.lang.Boolean]): Unit = {

    LOG.info(f"recordCount: $recordCount, endOfRecords: $endOfRecords")
    if (0 < recordCount.get()) {
	    val xmlResponseAsString = new String(Bytes.toArray(response))
	    val xmlResponse = XML.loadString(xmlResponseAsString)
	    val records = xmlResponse \\ "record"

	    records.map((record) => {
        val fieldMap = record.child.filter((node: Node) => { node.prefix == "darwin" && StringUtils.isNotEmpty(node.text) }).map( (x:Node) => { (x.label, x.text) }).toMap[String, String]
        emit(fieldMap)
      })

    }
    LOG.info("Received: {}", new String(Bytes.toArray(response)))
  }

}

class LoggingCrawlListener(
  val configuration: CrawlConfiguration,
  var lastContext: CrawlContext,
  var lastRequest: String,

  var totalRecordCount: Int,

  var startDate: java.util.Date) extends AbstractCrawlListener[ScientificNameRangeCrawlContext, String, java.util.List[java.lang.Byte]] {

  val LOG = LoggerFactory.getLogger(getClass)

  MDC.put("datasetKey", configuration.getDatasetKey.toString)
  MDC.put("attempt", String.valueOf(configuration.getAttempt))

  override def error(msg: String) {
    LOG.warn("error during crawling: [{}], last request [{}], message [{}]", lastContext, lastRequest, msg)
  }

  override def error(e: Throwable) {
    LOG.warn("error during crawling: [{}], last request [{}]", lastContext, lastRequest, e)
  }

  override def finishCrawlAbnormally() {
    finishCrawl(FinishReason.ABORT)
  }

  override def finishCrawlNormally() {
    finishCrawl(FinishReason.NORMAL)
  }

  override def finishCrawlOnUserRequest() {
    finishCrawl(FinishReason.USER_ABORT)
  }

  override def progress(context: ScientificNameRangeCrawlContext) {
    lastRequest = null
    lastContext = context
    LOG.info(f"now beginning to crawl [$context]")
  }

  override def request(req: String, retry: Int) {
    LOG.info(f"requested page for [$lastContext], retry [$retry], request [$req]")
    lastRequest = req
  }

  override def response(
    response: java.util.List[java.lang.Byte],
    retry: Int,
    duration: Long,
    recordCount: Optional[java.lang.Integer],
    endOfRecords: Optional[java.lang.Boolean]): Unit = {
    totalRecordCount += recordCount.or(0)
    val took = TimeUnit.MILLISECONDS.toSeconds(duration)
    LOG.info(f"got response for [$lastContext], records [$recordCount], endOfRecords [$endOfRecords], retry [$retry], took [${took}s]")
  }

  override def startCrawl() {
    this.startDate = new Date()
    LOG.info("started crawl")
  }

  def finishCrawl(reason: FinishReason.Value) {
    val finishDate = new Date()
    val minutes = (finishDate.getTime - startDate.getTime) / (60 * 1000)
    LOG.info(
      f"finished crawling with a total of [$totalRecordCount] records, reason [$reason], started at [$startDate], finished at [$finishDate], took [$minutes] minutes")

    MDC.remove("datasetKey")
    MDC.remove("attempt")
  }

  object FinishReason extends Enumeration {
    type FinishReason = Value

    val NORMAL = Value("Normal")
    val USER_ABORT = Value("User Abort")
    val ABORT = Value("Abort")
    val UNKNOWN = Value("Unknown")
  }

}

object HttpCrawlClientProvider {

  val DEFAULT_HTTP_PORT = 80

  val CONNECTION_TIMEOUT_MSEC = 600000 // 10 mins
  val MAX_TOTAL_CONNECTIONS = 500
  val MAX_TOTAL_PER_ROUTE = 20

  def newHttpCrawlClient(): HttpCrawlClient = {
    val schemeRegistry = new SchemeRegistry()
    schemeRegistry.register(new Scheme("http", DEFAULT_HTTP_PORT, PlainSocketFactory.getSocketFactory))

    val connectionManager = new PoolingClientConnectionManager(schemeRegistry)
    connectionManager.setMaxTotal(MAX_TOTAL_CONNECTIONS)
    connectionManager.setDefaultMaxPerRoute(MAX_TOTAL_PER_ROUTE)

    val params = new BasicHttpParams()
    params.setParameter(HttpConnectionParams.CONNECTION_TIMEOUT, CONNECTION_TIMEOUT_MSEC)
    params.setParameter(HttpConnectionParams.SO_TIMEOUT, CONNECTION_TIMEOUT_MSEC)
    params.setLongParameter(ClientPNames.CONN_MANAGER_TIMEOUT, CONNECTION_TIMEOUT_MSEC)
    val httpClient = new DecompressingHttpClient(new DefaultHttpClient(connectionManager, params))

    new HttpCrawlClient(connectionManager, httpClient)
  }

  def HttpCrawlClientProvider() {
    throw new UnsupportedOperationException("Can't initialize class")
  }

}



