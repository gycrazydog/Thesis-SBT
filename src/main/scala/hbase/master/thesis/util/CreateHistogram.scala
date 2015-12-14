package hbase.master.thesis.util
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark._
import org.apache.spark.graphx._
import scala.collection.immutable.HashSet
import unicredit.spark.hbase._
// To make some of the examples work we will also need RDD
import org.apache.spark.rdd.RDD
import org.apache.spark.graphx.PartitionStrategy._
import scala.collection.mutable.BitSet
import java.io._
import scala.io.Source
import scala.collection.mutable.Queue
import org.apache.hadoop.hbase.client.Scan
object CreateHistogram {
  def main(args:Array[String]) : Unit = {
      val dataset_path  = args(0)
      val sparkMaster = args(1)  
      val output_path = args(2)
      val sparkConf = new SparkConf().setAppName("Create Histogram: ").setMaster(sparkMaster)
      val sc = new SparkContext(sparkConf)
      val rdd = sc.textFile(dataset_path, 3).map(line=>{
                val edge = line.split(" ")
                (edge(2),1)
                }).reduceByKey(_+_).map(v=>v._1+" "+v._2).collect
      val writer = new PrintWriter(new File(output_path))  
      rdd.foreach(line=>writer.write(line+"\n"))
      writer.close()
  }
}