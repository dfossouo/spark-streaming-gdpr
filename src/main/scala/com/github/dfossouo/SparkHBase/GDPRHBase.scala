/*******************************************************************************************************
This code does the following:
  1) Read the same HBase Table from two distinct or identical clusters (without knowing the schema of tables)
  2) Extract specific time Range
  3) Create two dataframes and compare them to give back lines where the tables are differents (only the columns where we have differences)
Usage:
SPARK_MAJOR_VERSION=2 spark-submit --class com.github.dfossouo.SparkHBase.GDPRHBase --master yarn --deploy-mode client --driver-memory 2g --executor-memory 2g --executor-cores 1 --num-executors 2 --jars ./target/GDPRHBASE-0.0.2-SNAPSHOT.jar /usr/hdp/current/phoenix-client/phoenix-client.jar ./props
NB: props2 est un fichier qui contient les variables d'entrées du projet
  ********************************************************************************************************/

package com.github.dfossouo.SparkHBase

import java.util.Calendar

import org.apache.hadoop.hbase.{CellUtil, HBaseConfiguration, TableName}
import org.apache.hadoop.hbase.client._
import org.apache.hadoop.hbase.protobuf.ProtobufUtil
import org.apache.hadoop.hbase.util.Base64
import org.apache.spark.sql.execution.datasources.hbase._
import org.apache.spark.sql.functions._
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.hadoop.hbase.util.{Base64, Bytes}
import org.apache.spark.sql.Row
import org.apache.spark.sql.types.{IntegerType, StringType, StructField, StructType}

import scala.collection.mutable.HashMap
import scala.io.Source.fromFile


object GDPRHBase {

  case class hVar(rowkey: Int, colFamily: String, colQualifier: String, colDatetime: Long, colDatetimeStr: String, colType: String, colValue: String)

  // schema for sensor data
  case class reference(rowkey: String, level: String, bp: String, pdl: String, custid: String) extends Serializable

  def customer(str: String): reference = {
    val p = str.split(",")
    reference(p(0), p(1), p(2), p(3), p(4))
  }


  def main(args: Array[String]) {

    // Create difference between dataframe function
    import org.apache.spark.sql.DataFrame

    // Get Start time

    val start_time = Calendar.getInstance()
    println("[ *** ] Start Time: " + start_time.getTime().toString)

    // Init properties
    val props = getProps(args(0))

    // Get Start time table Scan
    val tbscan = 1540365233000L
    val start_time_tblscan = tbscan.toString()

    val path = props.get("zookeeper.znode.parent").get
    val table = props.get("hbase.table_gdpr.name").get
    val quorum = props.get("hbase.zookeeper.quorum").get

    // Create Spark Application
    val sparkConf = new SparkConf().setAppName("GDPRHBASE")
    sparkConf.set("spark.driver.allowMultipleContexts", "true")
    sparkConf.set("spark.shuffle.service.enabled", "false")
    sparkConf.set("spark.dynamicAllocation.enabled", "false")

    val sc = new SparkContext(sparkConf)
    val sqlContext = new org.apache.spark.sql.SQLContext(sc)
    import sqlContext.implicits._

    println("[ *** ] Creating Customers Scheduled for Deletion")

    print("connection created")

    // test scala

    print("[ ****** ] define schema table emp ")

    def table_cluster= s"""{
        "table":{"namespace":"default", "name":"$table"},
        "rowkey":"key",
        "columns":{
        "rowkey":{"cf":"rowkey", "col":"key", "type":"string"},
        "level":{"cf":"demographics", "col":"level", "type":"string"},
        "bp":{"cf":"demographics", "col":"bp", "type":"string"},
        "pdl":{"cf":"demographics", "col":"pdl", "type":"string"},
        "custid":{"cf":"demographics", "col":"custid", "type":"string"}
        }
        }""".stripMargin

    print("[ ****** ] define schema table customer_info ")


    print("[ ****** ] Create DataFrame table emp ")

    val connectionHbase = s"""{
        "hbase.zookeeper.quorum":"$quorum",
        "zookeeper.znode.parent":"$path"
      }
      """

    def withCatalogInfo(table_cluster: String): DataFrame = {
      sqlContext
        .read
        .options(Map(HBaseTableCatalog.tableCatalog->table_cluster, HBaseRelation.HBASE_CONFIGURATION -> connectionHbase))
        .format("org.apache.spark.sql.execution.datasources.hbase")
        .load()
    }

    print("[ ****** ] Create DataFrame table customer_info ")

    print("[ ****** ] declare DataFrame for table customer_info ")

    val df = withCatalogInfo(table_cluster)

    print("Here are the columns " + df.columns.foreach(println))

    df.columns.map(f => print(f))
    df.show(10,false)

    val ssc = new StreamingContext(sc, Seconds(10))

    df.write.format("csv").mode("overwrite").save("/user/hbase/df.csv")

    val schema =new StructType().add(StructField("bp",StringType,true))
      .add(StructField("pdl",IntegerType,true))
      .add(StructField("custid",StringType,true))
      .add(StructField("level",StringType,true))

    // parse the lines of data into customer objects
    val textDStream = ssc.textFileStream("/user/hbase/customers_deletion.csv")
    val schema2 =new StructType().add(StructField("bp",StringType,true)).add(StructField("custid",StringType,true)).add(StructField("level",StringType,true)).add(StructField("pdl",StringType,true))

    println("********" + "print each elements")
    val hConf = HBaseConfiguration.create()
    hConf.setInt("timeout", 120000)
    hConf.set("hbase.rootdir", props.getOrElse("hbase.rootdir", "/tmp"))
    hConf.set("zookeeper.znode.parent", props.getOrElse("zookeeper.znode.parent", "/hbase-unsecure"))
    hConf.set("hbase.zookeeper.quorum", props.getOrElse("hbase.zookeeper.quorum", "hdpcluster-15377-master-0.field.hortonworks.com:2181"))
    val hTable = new HTable(hConf,"clients_info_28112018")
    val cfPersonalData = Bytes.toBytes("demographics")
    val iban = Bytes.toBytes("iban")
    val street = Bytes.toBytes("street")
    // Create Connection
    val connection: Connection = ConnectionFactory.createConnection(hConf)

    textDStream.foreachRDD(rowsRDD => {
      val bpcol = rowsRDD.map(x => x.split(",")).map(x => Row(x(0)))
      bpcol.collect().foreach(i => {
        val record = i.mkString
        val put = new Put(Bytes.toBytes(record))
        put.add(cfPersonalData,iban,Bytes.toBytes("FRXX 2002 XXXX 2606 XXXX YYYY 606"))
        put.add(cfPersonalData,street,Bytes.toBytes("XX RUE DE PARIS 750XX PARIS"))
        hTable.put(put)

      })
    })


    // Start the computation
    ssc.start()
    // Wait for the computation to terminate
    ssc.awaitTermination()


  }

  def convertScanToString(scan: Scan) = {
    val proto = ProtobufUtil.toScan(scan);
    Base64.encodeBytes(proto.toByteArray());
  }

  def getArrayProp(props: => HashMap[String, String], prop: => String): Array[String] = {
    return props.getOrElse(prop, "").split(",").filter(x => !x.equals(""))
  }

  def getProps(file: => String): HashMap[String, String] = {
    var props = new HashMap[String, String]
    val lines = fromFile(file).getLines
    lines.foreach(x => if (x contains "=") props.put(x.split("=")(0), if (x.split("=").size > 1) x.split("=")(1) else null))
    props
  }


}
//WAKANDA FORVER