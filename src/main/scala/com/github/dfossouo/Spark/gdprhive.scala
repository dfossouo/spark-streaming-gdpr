package com.github.dfossouo.Spark

import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.streaming.kafka010.KafkaUtils
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe


object gdprhive extends App {

  val spark = SparkSession.builder.master("local[2]").appName("GDPRHive").getOrCreate

  val streamingContext = new StreamingContext(sc, Seconds(100))

  val sc = spark.sparkContext
  val sqlContext = spark.sqlContext
  val driverName = "org.apache.hive.jdbc.HiveDriver"
  Class.forName(driverName)
  val df = spark.read
    .format("jdbc")
    .option("url", "jdbc:hive2://hdpcluster-15377-compute-2.field.hortonworks.com:2181,hdpcluster-15377-master-0.field.hortonworks.com:2181,hdpcluster-15377-worker-1.field.hortonworks.com:2181,c125-node2.field.hortonworks.com:2181,c125-node4.field.hortonworks.com:2181,c125-node3.field.hortonworks.com:2181/;serviceDiscoveryMode=zooKeeper;zooKeeperNamespace=hiveserver2")
    .option("dbtable", "customers_deletion")
    .load()
  df.printSchema()
  println(df.count())
  df.show()

  streamingContext.start()
  streamingContext.awaitTermination()
}
