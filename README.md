# Spark Streaming GDPR

## Prerequisites 
### Scala 2.11
### Sbt 1.2.4 
### Java 1.8
### Spark 2.3.0 - from HDP 
### Maven 3.5.4

# PART I - Read Hive Table


# PART II - Delete found users in Hive Table & HBase Table
  
Usage: 

SPARK_MAJOR_VERSION=2 spark-submit --class com.github.dfossouo.Spark.gdprhive --master yarn --deploy-mode client --driver-memory 2g --executor-memory 3g --executor-cores 2 --num-executors 2 --jars ./target/HBaseCRC-0.0.2-SNAPSHOT.jar /tmp/phoenix-client.jar ./props


NB: props is the property file where the cluster properties are defined