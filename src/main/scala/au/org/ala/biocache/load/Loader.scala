package au.org.ala.biocache.load

import au.org.ala.biocache.Config
import scala.io.Source
import scala.util.parsing.json.JSON
import scala.collection.JavaConversions
import scala.collection.mutable.HashMap
import au.org.ala.biocache
import au.org.ala.biocache.cmd.{NoArgsTool, Tool, CMD}
import au.org.ala.biocache.util.{OptionParser, StringHelper}
import org.slf4j.LoggerFactory

/**
 * Runnable loader that just takes a resource UID and delegates based on the protocol.
 */
object Loader extends Tool {

  val logger = LoggerFactory.getLogger("Loader")
  def cmd = "load"
  def desc = "Load a data resource"

  def main(args:Array[String]){

    var dataResourceUid:String = ""
    var forceLoad = false
    var testLoad = false
    val parser = new OptionParser(help) {
      arg("data-resource-uid","The data resource to process", {v:String => dataResourceUid = v})
      booleanOpt("fl", "force-load", "Force the (re)load of media", {v:Boolean => forceLoad = v})
      booleanOpt("t", "test-load", "Test the (re)load of media", {v:Boolean => testLoad = v})
    }

    if(parser.parse(args)){
      logger.info("Starting to load resource: " + dataResourceUid)
      val l = new Loader
      l.load(dataResourceUid, testLoad, forceLoad)
      logger.info("Completed loading resource: " + dataResourceUid)
      biocache.Config.persistenceManager.shutdown
    }
  }
}

object Healthcheck extends NoArgsTool {
  def cmd = "healthcheck"
  def desc = "Run a health check on the configured resources"
  def main(args:Array[String]) = proceed(args, () => (new Loader()).healthcheck)
}

object ListResources extends NoArgsTool {
  def cmd = "list"
  def desc = "List configured data resources"
  def main(args:Array[String]) = proceed(args, () => (new Loader()).printResourceList)
}

object DescribeResource extends Tool {

  def cmd = "describe"
  def desc = "Describe the configuration for a data resource"

  def main(args:Array[String]){
    var dataResourceUid:String = ""
    val parser = new OptionParser(help) {
      arg("data-resource-uid", "The UID data resource to process, e.g. dr1", {v:String => dataResourceUid = v})
    }

    if(parser.parse(args)){
      println("Starting to load resource: " + dataResourceUid)
      val l = new Loader
      l.describeResource(List(dataResourceUid))
      println("Completed loading resource: " + dataResourceUid)
      biocache.Config.persistenceManager.shutdown
    }
  }
}

class Loader extends DataLoader {

  import JavaConversions._
  import StringHelper._

  def describeResource(drlist:List[String]){
    drlist.foreach(dr => {
      val (protocol, url, uniqueTerms, params, customParams, lastChecked) = retrieveConnectionParameters(dr)
      println("UID: " + dr)
      println("This data resource was last checked " + lastChecked)
      println("Protocol: "+ protocol)
      println("URL: " + url.mkString(";"))
      println("Unique terms: " + uniqueTerms.mkString(","))
      params.foreach { println(_)}
      customParams.foreach{ println(_)}
      println("---------------------------------------")
    })
  }

  def printResourceList {
    if(!resourceList.isEmpty){
      CMD.printTable(resourceList)
    } else {
      println("No resources are registered in the registry.")
    }
  }

  def resourceList : List[Map[String, String]] = {
    val json = Source.fromURL(Config.registryUrl + "/dataResource?resourceType=records").getLines.mkString
    val drs = JSON.parseFull(json).get.asInstanceOf[List[Map[String, String]]]
    drs
  }

  def load(dataResourceUid: String, test:Boolean=false, forceLoad:Boolean=false) {
    try {
      val (protocol, url, uniqueTerms, params, customParams, lastChecked) = retrieveConnectionParameters(dataResourceUid)
      protocol.toLowerCase match {
        case "dwc" => {
          logger.info("Darwin core headed CSV loading")
          val l = new DwcCSVLoader
          l.load(dataResourceUid, false,test,forceLoad)
        }
        case "dwca" => {
          logger.info("Darwin core archive loading")
          val l = new DwCALoader
          l.load(dataResourceUid, false,test,forceLoad)
        }
        case "digir" => {
          logger.info("digir webservice loading")
          val l = new DiGIRLoader
          l.load(dataResourceUid, test)
        }
        case "flickr" => {
          logger.info("flickr webservice loading")
          val l = new FlickrLoader
          if(!test)
            l.load(dataResourceUid, true)
          else
            println("TESTING is not supported for Flickr")
        }
        case "customwebservice" => {
          logger.info("custom webservice loading")
          if(!test){
            val className = customParams.getOrElse("classname", null)
            if (className == null) {
              println("Classname of custom harvester class not present in parameters")
            } else {
              val wsClass = Class.forName(className)
              val l = wsClass.newInstance()
              if (l.isInstanceOf[CustomWebserviceLoader]) {
                l.asInstanceOf[CustomWebserviceLoader].load(dataResourceUid)
              } else {
                println("Class " + className + " is not a subtype of au.org.ala.util.CustomWebserviceLoader")
              }
            }
          } else {
            println("TESTING is not supported for custom web service")
          }
        }
        case "autofeed" => {
          logger.info("AutoFeed Darwin core headed CSV loading")
          val l = new AutoDwcCSVLoader
          if(!test)
            l.load(dataResourceUid, forceLoad=forceLoad)
          else
            logger.warn("TESTING is not supported for auto-feed")
        }
        case _ => logger.warn("Protocol " + protocol + " currently unsupported.")
      }
    } catch {
      //NC 2013-05-10: Need to rethrow the exception to allow the tools to allow the tools to pick up on them.
      case e: Exception => logger.error(e.getMessage(), e); throw e
    }

    if(test){
      println("Check the output for any warning/error messages.")
      println("If there are any new institution and collection codes ensure that they are handled correctly in the Collectory.")
      println("""Don't forget to check that number of NEW records.  If this is high for updated data set it may indicate that the "unique values" have changed format.""")
    }
  }

  def healthcheck = {
    val json = Source.fromURL(Config.registryUrl + "/dataResource/harvesting", "UTF-8").getLines.mkString
    val drs = JSON.parseFull(json).get.asInstanceOf[List[Map[String, String]]]
    // UID, name, protocol, URL,
    val digirCache = new HashMap[String, Map[String, String]]()
    //iterate through the resources
    drs.foreach(dr => {

      val drUid = dr.getOrElse("uid", "")
      val drName = dr.getOrElse("name", "")
      val connParams  =  dr.getOrElse("connectionParameters", Map[String,AnyRef]()).asInstanceOf[Map[String,AnyRef]]

      if(connParams != null){
        val protocol = connParams.getOrElse("protocol", "").asInstanceOf[String].toLowerCase

        val urlsObject = connParams.getOrElse("url", List[String]())
        val urls:Seq[String] = if(urlsObject.isInstanceOf[Seq[_]]){
          urlsObject.asInstanceOf[Seq[String]]
        } else {
          List(connParams("url").asInstanceOf[String])
        }

        urls.foreach(url => {
          val status = protocol match {
            case "dwc" => checkArchive(drUid, url)
            case "dwca" => checkArchive(drUid, url)
            case "digir" => {
              if(url == null || url ==""){
                Map("Status" -> "NOT CONFIGURED")
              } else if(!digirCache.get(url).isEmpty){
                digirCache.get(url).get
              } else {
                val result = checkDigir(drUid, url)
                digirCache.put(url,result)
                result
              }
            }
            case _ => Map("Status" -> "IGNORED")
          }

          if(status.getOrElse("Status", "NO STATUS") != "IGNORED"){

            val fileSize = status.getOrElse("Content-Length", "N/A")
            val displaySize = {
              if(fileSize!= "N/A"){
                (fileSize.toInt / 1024).toString +"kB"
              } else {
                fileSize
              }
            }
            println(drUid.fixWidth(5)+ "\t"+protocol.fixWidth(8)+"\t" +
                    status.getOrElse("Status", "NO STATUS").fixWidth(15) +"\t" + drName.fixWidth(65) + "\t" + url.fixWidth(85) + "\t" +
                    displaySize)
          }
        })
      }
    })
  }

  def checkArchive(drUid:String, url:String) : Map[String,String] = {
    if(url != ""){
      val conn = (new java.net.URL(url)).openConnection()
      val headers = conn.getHeaderFields()
      val map = new HashMap[String,String]
      headers.foreach({case(header, values) => {
        map.put(header, values.mkString(","))
      }})
      map.put("Status", "OK")
      map.toMap
    } else {
      Map("Status" -> "UNAVAILABLE")
    }
  }

  def checkDigir(drUid:String, url:String) : Map[String,String] = {

    try {
      val conn = (new java.net.URL(url)).openConnection()
      val headers = conn.getHeaderFields()
      val hdrs = new HashMap[String,String]()
      headers.foreach( {case(header, values) => {
          hdrs.put(header,values.mkString(","))
      }})
      (hdrs + ("Status" -> "OK")).toMap
    } catch {
      case e:Exception => {
        e.printStackTrace
        Map("Status" -> "UNAVAILABLE")
      }
    }
  }
}