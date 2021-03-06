package au.org.ala.biocache.index

import org.slf4j.LoggerFactory
import au.org.ala.biocache.util.OptionParser
import au.org.ala.biocache.Config
import scala.collection.mutable.ArrayBuffer
import au.org.ala.biocache.cmd.Tool
import org.apache.lucene.store.FSDirectory
import java.io.File
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.IndexWriterConfig.OpenMode
import org.apache.lucene.util.Version
import org.apache.commons.io.FileUtils

/**
 * A multi-threaded bulk processor that uses the search indexes to create a set a
 * ranges for record IDs. These ranges are then passed to individual threads
 * for processing.
 *
 * This tools is used for:
 *
 * 1) Reprocessing the entire dataset.
 * 2) Resampling the entire dataset.
 * 3) Creating a brand new complete index offline.
 */
object BulkProcessor extends Tool with Counter with RangeCalculator {

  def cmd = "bulk-processor"
  def desc = "Bulk processor for regenerating indexes and reprocessing the entire cache"

  override val logger = LoggerFactory.getLogger("BulkProcessor")

  def main(args: Array[String]) {

    var numThreads = 8
    var pageSize = 200
    var dirPrefix = "/data/biocache-reindex"
    var keys: Option[Array[String]] = None
    var columns: Option[Array[String]] = None
    var action = ""
    var start, end = ""
    var dr: Option[String] = None
    val validActions = List("range", "process", "index", "col", "repair", "datum", "load-sampling")
    var forceMerge = true
    var mergeSegments = 1
    var deleteSources = false

    val parser = new OptionParser(help) {
      arg("<action>", "The action to perform. Supported values :  range, process or index, col", {
        v: String => action = v
      })
      intOpt("t", "threads", "The number of threads to perform the indexing on. Default is " + numThreads, {
        v: Int => numThreads = v
      })
      intOpt("ps", "pagesize", "The page size for the records. Default is " + pageSize, {
        v: Int => pageSize = v
      })
      opt("p", "prefix", "The prefix to apply to the solr directories. Default is " + dirPrefix, {
        v: String => dirPrefix = v
      })
      opt("k", "keys", "A comma separated list of keys on which to perform the range threads. Prevents the need to query SOLR for the ranges.", {
        v: String => keys = Some(v.split(","))
      })
      opt("s", "start", "The rowKey in which to start the range", {
        v: String => start = v
      })
      opt("e", "end", "The rowKey in which to end the range", {
        v: String => end = v
      })
      opt("dr", "dr", "The data resource over which to obtain the range", {
        v: String => dr = Some(v)
      })
      opt("c", "columns", "The columns to export", {
        v: String => columns = Some(v.split(","))
      })
      booleanOpt("fm", "forceMerge", "Force merge of segments. Default is " + forceMerge + ". For index only.", {
        v: Boolean => forceMerge = v
      })
      intOpt("ms", "max segments", "Max merge segments. Default " + mergeSegments + ". For index only.", {
        v: Int => mergeSegments = v
      })
      booleanOpt("ds", "delete-sources", "Delete sources if successful. Defaults to " + deleteSources + ". For index only.", {
        v: Boolean => deleteSources = v
      })
    }

    if (parser.parse(args)) {
      if (validActions.contains(action)) {
        val (query, start, end) = if (dr.isDefined){
          ("data_resource_uid:" + dr.get, dr.get + "|", dr.get + "|~")
        } else {
          ("*:*", "", "")
        }

        val ranges = if (keys.isEmpty){
          calculateRanges(numThreads, query, start, end)
        } else {
          generateRanges(keys.get, start, end)
        }

        if (action == "range") {
          logger.info(ranges.mkString("\n"))
        } else if (action != "range") {
          var counter = 0
          val threads = new ArrayBuffer[Thread]
          val columnRunners = new ArrayBuffer[ColumnReporterRunner]
          val solrDirs = new ArrayBuffer[String]
          ranges.foreach(r => {
            logger.info("start: " + r._1 + ", end key: " + r._2)

            val ir = {
              if (action == "datum") {
                new DatumRecordsRunner(this, counter, r._1, r._2)
              } else if (action == "repair") {
                new RepairRecordsRunner(this, counter, r._1, r._2)
              } else if (action == "index") {
                solrDirs += (dirPrefix + "/solr-create/biocache-thread-" + counter + "/data/index")
                new IndexRunner(this,
                  counter,
                  r._1,
                  r._2,
                  dirPrefix + "/solr-template/biocache/conf",
                  dirPrefix + "/solr-create/biocache-thread-" + counter + "/conf",
                  pageSize
                )
              } else if (action == "process") {
                new ProcessRecordsRunner(this, counter, r._1, r._2)
              } else if (action == "load-sampling") {
                new LoadSamplingRunner(this, counter, r._1, r._2)
              } else if (action == "col") {
                if (columns.isEmpty) {
                  new ColumnReporterRunner(this, counter, r._1, r._2)
                } else {
                  new ColumnExporter(this, counter, r._1, r._2, columns.get.toList)
                }
              } else {
                new Thread()
              }
            }
            val t = new Thread(ir)
            t.start
            threads += t
            if (ir.isInstanceOf[ColumnReporterRunner]) {
              columnRunners += ir.asInstanceOf[ColumnReporterRunner]
            }
            counter += 1
          })

          //wait for threads to complete and merge all indexes
          threads.foreach(thread => thread.join)

          if (action == "index") {
            //TODO - might be worth avoiding optimisation
            IndexMergeTool.merge(dirPrefix + "/solr/merged", solrDirs.toArray, forceMerge, mergeSegments, deleteSources)
            Config.persistenceManager.shutdown
            logger.info("Waiting to see if shutdown")
            System.exit(0)
          } else if (action == "col") {
            var allSet: Set[String] = Set()
            columnRunners.foreach(c => allSet ++= c.myset)
            allSet = allSet.filterNot(it => it.endsWith(".p") || it.endsWith(".qa"))
            logger.info("All set: " + allSet)
          }
        }
      }
    }
  }
}

/**
 * Thin wrapper around SOLR index merge tool that allows it to be incorporated
 * into the CMD2.
 *
 * TODO add support for directory patterns e.g. /data/solr-create/biocache-thread-{wildcard}/data/index
 */
object IndexMergeTool extends Tool {

  def cmd = "index-merge"
  def desc = "Merge indexes "
  val logger = LoggerFactory.getLogger("IndexMergeTool")

  def main(args:Array[String]){

    var mergeDir = ""
    var directoriesToMerge = Array[String]()
    var forceMerge = true
    var mergeSegments = 1
    var deleteSources = false
    var ramBuffer = 4096.0d

    val parser = new OptionParser(help) {
      arg("<merge-dr>", "The output path for the merged index", {
        v: String => mergeDir = v
      })
      arg("<to-merge>", "Pipe separated list of directories to merge", {
        v: String => directoriesToMerge = v.split('|').map(x => x.trim)
      })
      booleanOpt("fm", "forceMerge", "Force merge of segments. Default is " + forceMerge, {
        v: Boolean => forceMerge = v
      })
      intOpt("ms", "max-segments", "Max merge segments. Default " + mergeSegments, {
        v: Int => mergeSegments = v
      })
      doubleOpt("ram", "ram-buffer", "RAM buffer size. Default " + ramBuffer, {
        v: Double => ramBuffer = v
      })
      booleanOpt("ds", "delete-sources", "Delete sources if successful. Defaults to " + deleteSources, {
        v: Boolean => deleteSources = v
      })
    }
    if (parser.parse(args)) {
      merge(mergeDir, directoriesToMerge, forceMerge, mergeSegments, deleteSources, ramBuffer)
    }
  }

  /**
   * Merge method that wraps SOLR merge API
   * @param mergeDir
   * @param directoriesToMerge
   * @param forceMerge
   * @param mergeSegments
   */
  def merge(mergeDir: String, directoriesToMerge: Array[String], forceMerge: Boolean, mergeSegments: Int, deleteSources:Boolean, rambuffer:Double = 4096.0d) {
    val start = System.currentTimeMillis()

    logger.info("Merging to directory:  " + mergeDir)
    directoriesToMerge.foreach(x => println("Directory included in merge: " + x))

    val mergeDirFile = new File(mergeDir)
    if (mergeDirFile.exists()) {
      //clean out the directory
      mergeDirFile.listFiles().foreach(f => FileUtils.forceDelete(f))
    } else {
      FileUtils.forceMkdir(mergeDirFile)
    }

    val mergedIndex = FSDirectory.open(mergeDirFile)

    val writerConfig = (new IndexWriterConfig(Version.LUCENE_CURRENT, null))
      .setOpenMode(OpenMode.CREATE)
      .setRAMBufferSizeMB(rambuffer)

    val writer = new IndexWriter(mergedIndex, writerConfig)
    val indexes = directoriesToMerge.map(dir => FSDirectory.open(new File(dir)))

    logger.info("Adding indexes...")
    writer.addIndexes(indexes:_*)

    if (forceMerge) {
      logger.info("Full merge...")
      writer.forceMerge(mergeSegments)
    } else {
      logger.info("Skipping merge...")
    }

    writer.close()
    val finish = System.currentTimeMillis()
    logger.info("Merge complete:  " + mergeDir + ". Time taken: " +((finish-start)/1000)/60 + " minutes")

    if(deleteSources){
      logger.info("Deleting source directories")
      directoriesToMerge.foreach(dir => FileUtils.forceDelete(new File(dir)))
      logger.info("Deleted source directories")
    }
  }
}