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
object PrintGraphStructure {
  var dataset_path = "";
  var sparkMaster = "";
  var partition_path = "";
  var inputnodes : Set[String] = new HashSet()
  implicit val config = HBaseConfig()
  def fromHBase(): Unit = {
    val sparkConf = new SparkConf().setAppName("JabeJa : ").setMaster(sparkMaster)
    val sc = new SparkContext(sparkConf)
    val tableName = "test1"
    val columns = Map(
      "property"   ->  Set("inputnode")    
    )
    val rdd = sc.hbase[String](tableName,columns)
    inputnodes = rdd.filter(v=>v._2.get("property").get.contains("inputnode")).map(v=>v._1).collect().toSet
    println("input node size "+inputnodes.size)
    val edgerdd = sc.hbase[String](tableName,Set("to"))
                        .flatMap(v=>v._2.values.map(k=>(v._1,k)))
                        .flatMap(v=>v._2.map(k=>(v._1,k._1,k._2)))
                        .flatMap(v=>v._3.split(":").map(k=>(v._1,k)))
    edgerdd.collect.foreach(println)
    println("cross edge size "+edgerdd.filter(v=>v._1.charAt(0)!=v._2.charAt(0)).count)
  }
  def fromFile():Unit = {
      val sparkConf = new SparkConf().setAppName("JabeJa : ").setMaster(sparkMaster)
      val sc = new SparkContext(sparkConf)
      val edges = sc.textFile(dataset_path, 3)
                    .map(line=>{
                          val edge = line.split(" ")
                          Edge(edge(0).toLong,edge(1).toLong,edge(2))
                     })
                .collect
      val files = new File(partition_path).list.filter(name=>false==name.endsWith("txt")&&false==name.endsWith("txt~"))
      files.foreach(f=>{
        println("current configuration : "+f)
        val color = Source.fromFile(partition_path+"/"+f).getLines().toArray.map(_.toInt)
        println("inputnode start input nodes : " + edges.filter( e=>color(e.dstId.toInt)!=color(e.srcId.toInt) )
                                                   .map(x=>x.dstId).toList.removeDuplicates.size )
        println("start cross edges : " + edges.filter( e=>color(e.dstId.toInt)!=color(e.srcId.toInt) ).size )
      })
  }
  def main(args:Array[String]) : Unit = {
      dataset_path  = args(0)
      sparkMaster = args(1)  
      partition_path = args(2)
      fromHBase
  }
  
}