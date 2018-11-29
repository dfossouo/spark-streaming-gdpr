/*******************************************************************************************************
This code does the following:
  1) Read the same HBase Table from two distinct or identical clusters (without knowing the schema of tables)
  2) Extract specific time Range
  3) Create two dataframes and compare them to give back lines where the tables are differents (only the columns where we have differences)
Usage:
SPARK_MAJOR_VERSION=2 spark-submit --class com.github.dfossouo.SparkHBase.GDPRHBase --master yarn --deploy-mode client --driver-memory 2g --executor-memory 2g --executor-cores 1 --num-executors 2 --jars ./target/GDPRHBASE-0.0.2-SNAPSHOT.jar /usr/hdp/current/phoenix-client/phoenix-client.jar ./props
NB: props2 est un fichier qui contient les variables d'entrées du projet
  ********************************************************************************************************/

package com.github.dfossouo.SparkHive

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


object GDPRHive {

  def main(args: Array[String]) {

    // Create difference between dataframe function
    import org.apache.spark.sql.DataFrame

    // Get Start time

    val start_time = Calendar.getInstance()
    println("[ *** ] Start Time: " + start_time.getTime().toString)

    // Init properties
    val props = getProps(args(0))

    val path = props.get("zookeeper.znode.parent").get
    val table = props.get("hbase.table_gdpr.name").get

    // Create Spark Application
    val sparkConf = new SparkConf().setAppName("GDPRHIVE")
    sparkConf.set("spark.driver.allowMultipleContexts", "true")
    sparkConf.set("spark.shuffle.service.enabled", "false")
    sparkConf.set("spark.dynamicAllocation.enabled", "false")

    val sc = new SparkContext(sparkConf)
    val sqlContext = new org.apache.spark.sql.SQLContext(sc)
    import sqlContext.implicits._

    val ssc = new StreamingContext(sc, Seconds(10))

    // parse the lines of data into customer objects
    val textDStream = ssc.textFileStream("/user/hbase/customers_deletion.csv")

    println("********" + "print each elements")

    textDStream.foreachRDD(rowsRDD => {
      val bpcol = rowsRDD.map(x => x.split(",")).map(x => Row(x(0)))
      bpcol.collect().foreach(i => {
        val record = i.mkString

        sqlContext.sql("UPDATE "+ table + "\nSET iban='FRXX 2002 XXXX 2606 XXXX YYYY 606' \nWHERE bp = " + record)
        sqlContext.sql("UPDATE "+ table + "\nSET street='XX RUE DE PARIS 750XX PARIS' \nWHERE bp = " + record)

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