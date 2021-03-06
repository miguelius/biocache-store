package au.org.ala.biocache.export

import au.org.ala.biocache.Config
import scala.Array._
import au.com.bytecode.opencsv.CSVWriter
import java.io.{FileWriter,  File}
import scala.collection.mutable.HashSet
import org.apache.commons.lang.StringUtils
import org.slf4j.LoggerFactory
import au.org.ala.biocache.util.{Json, OptionParser}
import au.org.ala.biocache.load.FullRecordMapper
import au.org.ala.biocache.cmd.Tool

/**
 * Utility for exporting data from the biocache database.
 */
object ExportUtil extends Tool {

  def cmd = "export"
  def desc = "Export data as CSV or JSON"

  val logger = LoggerFactory.getLogger("ExportUtil")

  def main(args: Array[String]) {

    var fieldsToExport = List[String]()
    var fieldsRequired = List[String]()
    var includeRowKey = true
    var charSeparator = '\t'
    var entity = ""
    var filePath = ""
    var startkey =""
    var endkey = ""
    var distinct = false
    var json = false
    var maxRecords = Integer.MAX_VALUE

    val parser = new OptionParser(help) {
      arg("entity", "the entity (column family in cassandra) to export from. e.g. occ", { v: String => entity = v })
      arg("file-path", "file to export to", { v: String => filePath = v })
      opt("c", "columns", "<column1 column2 ...>", "space separated list of columns to export", {
        columns: String => fieldsToExport = columns.split(" ").toList
      })
      opt("r", "required-columns", "<column1 column2 ...>", "space separated required columns", {
        columns: String => fieldsRequired = columns.split(" ").toList
      })
      booleanOpt("rk", "include-rowkey", "Include the row key in the export", {s:Boolean => includeRowKey = s})
      opt("sc", "separator-char", "Separator char to use. Defaults to tab", {s:String => charSeparator = s.trim.charAt(0)})
      opt("s", "start", "The row key to start with", {s:String => startkey = s})
      opt("e", "end", "The row key to end with", {s:String => endkey = s})
      opt("distinct", "distinct values for the columns only", { distinct = true })
      opt("json", "export the values as json", { json = true })
      intOpt("m", "max-records", "number of records to export", { v: Int => maxRecords = v })
    }

    if (parser.parse(args)) {
      val outWriter = new FileWriter(new File(filePath))
      val writer = new CSVWriter(outWriter, charSeparator, '"')
      if(json) {
        exportJson(outWriter, entity, startkey, endkey, maxRecords)
      } else if(distinct) {
        exportDistinct(writer, entity, fieldsToExport, startkey, endkey)
      } else {
        export(writer, entity, fieldsToExport, fieldsRequired, List(), maxRecords = maxRecords, includeRowKey = includeRowKey)
      }
      writer.flush
      writer.close
    }
  }
  
  def exportJson(writer:FileWriter,entity:String, startKey:String, endKey:String, maxRecords:Int){
    
    val pm = Config.persistenceManager
    var counter=0
    pm.pageOverAll(entity, (guid,map) =>{
      val finalMap = map +(entity+"rowKey"->guid)
      //println(Json.toJSON(finalMap))
      writer.write(Json.toJSON(finalMap))
      writer.write("\n")
      counter += 1
      maxRecords > counter
    },startKey,endKey,1000)
    writer.flush
    writer.close
  }
  
  def exportDistinct(writer: CSVWriter, entity:String, fieldsToExport:List[String], startUuid:String="", endUuid:String="")={
    val pm = Config.persistenceManager
    val valueSet = new scala.collection.mutable.HashSet[String]
    pm.pageOverSelect(entity, (guid, map) =>{
      val line = (for (field <- fieldsToExport) yield map.getOrElse(field, ""))
      val sline:String = line.mkString(",")
      if(!valueSet.contains(sline)){
        valueSet += sline
        writer.writeNext(line.toArray)
      }
      true
    }, startUuid, endUuid, 1000, fieldsToExport: _*)
  }

  def export(writer: CSVWriter, entity: String,
             fieldsToExport: List[String],
             fieldsRequired: List[String],
             nonNullFields:List[String],
             defaultMappings:Option[Map[String,String]]=None,
             startUuid:String = "",
             endUuid:String = "",
             maxRecords: Int,
             includeDeleted:Boolean = false,
             includeRowKey:Boolean = true) {

    val pm = Config.persistenceManager
    var counter = 0
    val newFields:List[String] = if(defaultMappings.isEmpty) fieldsToExport ++ List(FullRecordMapper.deletedColumn) else fieldsToExport ++ defaultMappings.get.values ++ List(FullRecordMapper.deletedColumn)
    
    //page through and create the index
    pm.pageOverSelect(entity, (guid, map) => {
      if(includeDeleted || map.getOrElse(FullRecordMapper.deletedColumn, "false").equals("false")){
        if (fieldsRequired.forall(field => map.contains(field)) && nonNullFields.forall(field => StringUtils.isNotBlank(map.getOrElse(field,"")))) {
          exportRecord(writer, fieldsToExport, guid, map, includeRowKey)
        }
        counter += 1
        if(counter % 10000 == 0){
          logger.info("Exported " + counter + " Last key " + guid)
        }
      }
      maxRecords > counter
    }, startUuid,endUuid, 1000, newFields: _*)
  }

  def exportRecord(writer: CSVWriter, fieldsToExport: List[String], guid: String, map: Map[String, String], includeRowKey:Boolean = true) {
    val fields = (for (field <- fieldsToExport) yield map.getOrElse(field, "")).toArray
    val line:Array[String]  = if(includeRowKey){
      Array(guid) ++ fields
    } else {
      fields
    }
    writer.writeNext(line)
  }
}