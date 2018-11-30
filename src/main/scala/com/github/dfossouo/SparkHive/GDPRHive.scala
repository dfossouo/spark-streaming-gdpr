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
import org.apache.spark.sql.SparkSession
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
    val table = props.get("hive.table_gdpr.name").get

    val spark = SparkSession
      .builder()
      .appName("GDPRHIVE")
      .enableHiveSupport()
      .getOrCreate()

    import spark.implicits._

    val sc = spark.sparkContext
    val ssc = new StreamingContext(sc, Seconds(10))

    // parse the lines of data into customer objects
    val textDStream = ssc.textFileStream("/user/hbase/customers_deletion.csv")

    val iban_static = "FRXX 2002 XXXX 2606 XXXX YYYY 606"

    println("********" + "print each elements")

    textDStream.foreachRDD(rowsRDD => {
      val bpcol = rowsRDD.map(x => x.split(",")).map(x => Row(x(0)))
      bpcol.collect().foreach(i => {
        val record = i.mkString

        // GET THE ROW TO DELETE AND KEEP ROW VALUES
        val client_table = spark.sql("SELECT * FROM default." + table + " WHERE bp = " + record + " LIMIT 1")
        val age = client_table.select("age").map(row => row.mkString).collect
        print(" ********************* the customer age is " + age.mkString + " ********************* ")

        val custid = client_table.select("custid").map(row => row.mkString).collect
        print(" ********************* the customer custid is " + custid.mkString + " ********************* ")

        val gender = client_table.select("gender").map(row => row.mkString).collect
        print(" ********************* the customer gender is " + gender.mkString + " ********************* ")

        val iban = client_table.select("iban").map(row => row.mkString).collect
        print(" ********************* the customer iban is " + iban.mkString + " ********************* ")

        val level = client_table.select("level").map(row => row.mkString).collect
        print(" ********************* the customer level is " + level.mkString + " ********************* ")

        val pdl = client_table.select("pdl").map(row => row.mkString).collect
        print(" ********************* the customer pdl is " + pdl.mkString + " ********************* ")

        // DELETE THE ROW WHERE THE BP HAVE BEEN SCHEDULED FOR DELETION
        spark.sql("ALTER TABLE " + table + " DROP IF EXISTS PARTITION (bp ='" + record +"')")
        //spark.sql("DELETE FROM "+ table + " WHERE bp = " + record)
        // INSERT THE ROW WITH UPDATE FIELDS
        spark.sql("use default")
        spark.sql("set hive.exec.dynamic.partition=true")
        spark.sql("set hive.exec.dynamic.partition.mode=nonstrict")
        spark.sql("set hive.enforce.bucketing=false")
        spark.sql("set hive.enforce.sorting=false")
        spark.sql("INSERT INTO "+ table + " PARTITION(bp) VALUES ('" + age.mkString + "','" + custid.mkString + "','" + gender.mkString + "','" + iban_static + "','" + level.mkString + "','" + pdl.mkString + "','" + record + "')")

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