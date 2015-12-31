package hbase.master.thesis.util
import unicredit.spark.hbase._
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark._
import org.apache.spark.graphx._
import scala.Product
import org.apache.hadoop.hbase.client.Scan
import org.apache.hadoop.hbase.util.Bytes
import org.apache.hadoop.hbase.filter.PrefixFilter
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer

import org.apache.hadoop.conf.Configuration;

import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.util.Bytes;

import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;


object HBaseConnectorTest {
  var tableName = ""
  implicit val config = HBaseConfig(
    "hbase.rootdir" -> "hdfs://hadoop-m:8020/hbase",
    "hbase.zookeeper.quorum" -> "hadoop-m"
  )
  def solveAndMerge(sc: SparkContext) = {
    val startTime = System.currentTimeMillis 
    val columns = Map(
      "to"   ->  Set("0")    
    )
    val column1 = Map(
      "to"   ->  Set("1")    
    )
    val hBaseRDD = sc.hbase[String]("test", columns)
    val hBaseRDD1 = sc.hbase[String]("test", column1)
    val finalRDD = hBaseRDD.union(hBaseRDD1)
    println("aaahahahaha "+finalRDD.count())
    val endTime = System.currentTimeMillis
    println("time : "+(endTime-startTime))
  }
  def solveInOneGo(sc: SparkContext) = {
    val list = Seq("inputnode")
    val columns = Map(
      "to"   ->  Set("0")    
    )
    val startTime = System.currentTimeMillis 
    val hBaseRDD = sc.hbase[String](tableName, columns)
    println(hBaseRDD.count)
     val endTime = System.currentTimeMillis
    println("time : "+(endTime-startTime))
//    hBaseRDD.collect().foreach(println)
  }
  def multipleScan(sc:SparkContext) = {
    val columns = Map(
      "to"   ->  Set("1")    
    )
    val startTime = System.currentTimeMillis 
    val filter = new PrefixFilter(Bytes.toBytes("abc"))
    val scan = new Scan()
    scan.setStartRow(Bytes.toBytes("a000000"))
    scan.setStopRow(Bytes.toBytes("a000016"))
    val rdd = sc.hbase[String]("test",columns,scan)
    println("partitions : "+rdd.partitions.size)
    rdd.foreachPartition(v=>println("number "+v.size))
    val endTime = System.currentTimeMillis
//    rdd.collect().foreach(println)
    println("time : "+(endTime-startTime))
  }
  def simpleTest(sc:SparkContext) = {
    
  }
  def main(args:Array[String]) = {
    tableName = args(0)
    val sparkConf = new SparkConf().setAppName("HBaseTest : ").setMaster("local[3]")
    val sc = new SparkContext(sparkConf)
    println(config.get.get("hbase.zookeeper.quorum"))
//    simpleTest(sc)
    solveInOneGo(sc)
//    multipleScan(sc)
//    var rowranges = ListBuffer(new MultiRowRangeFilter.RowRange("a000002",true,"a000010",true),new MultiRowRangeFilter.RowRange("a000012",true,"a000020",true))
//    var multiRowFilter = new MultiRowRangeFilter(rowranges.asJava)
//    val columns = Map(
//      "to"   -> Set("0")    
//    )
//    val rdd = sc.hbase[String]("test",columns,multiRowFilter)
//    rdd.collect().foreach(println)

  }
}