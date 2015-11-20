package hbase.master.thesis.util
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark._
import org.apache.spark.graphx._
import scala.collection.mutable.HashMap
import scala.collection.immutable.HashSet
import java.io._
import scala.io.Source
// To make some of the examples work we will also need RDD
import org.apache.spark.rdd.RDD
import org.apache.spark.graphx.PartitionStrategy._
object MetisGraphGenerator {
   var vertexPartitions : HashMap[Int,List[Int]] = new HashMap()
  var vertexSet : HashSet[Int] = HashSet()
  var vertexList : List[Int] = List()
  var sparkMaster = ""
  var cassandraMaster = ""
  var path = ""
  var tableName = "testgraph";
  var output_path = ""
  def createMitsGraph(sc:SparkContext){
    val leftright = sc.textFile(path, 3)
                    .map(line=>line.split(" "))
                    .map(line=>(line(0).toInt+1,line(1).toInt+1))
//                    .filter(f=>f._1<=26025&&f._2<=26025)
                    .groupByKey()
                    .map(f=>(f._1,f._2.mkString(" ")))
    val rightleft = sc.textFile(path, 3)
                    .map(line=>line.split(" "))
                    .map(line=>(line(1).toInt+1,line(0).toInt+1))
//                    .filter(f=>f._1<=26025&&f._2<=26025)
                    .groupByKey()
                    .map(f=>(f._1,f._2.mkString(" ")))        
    val lines = leftright.union(rightleft).reduceByKey((x,y)=>x+" "+y).sortByKey(true).collect()
//    lines.foreach(println)
//    val writer = new PrintWriter(new File("/home/crazydog/alibaba-dataset/metisgraph/alibaba.input.metis.txt"))
    val writer = new PrintWriter(new File(output_path))
    var current = 0
    lines.foreach(v=>{
      current = current+1
      while(current<v._1){
        writer.write("\n")
        current = current+1
      }
      writer.write(v._2+"\n")
    })
//    while(current<52050){
//      writer.write("\n")
//      current = current+1
//    }
    writer.close()
  }
  def main(args:Array[String]) = {
      path  = args(0)
      sparkMaster = args(1)  
      output_path = args(2)
      val sparkConf = new SparkConf().setAppName("JabeJa : ").setMaster(sparkMaster)
      val sc = new SparkContext(sparkConf)
  //    JabeJa_Local
  //    JabeJa_Random
      println("JabeJa_Random!")
      createMitsGraph(sc)
    }
}