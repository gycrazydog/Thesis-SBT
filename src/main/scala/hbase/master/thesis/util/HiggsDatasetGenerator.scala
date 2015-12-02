package hbase.master.thesis.util
import org.apache.spark.SparkContext._
import org.apache.spark._
import org.apache.spark.graphx._
import org.apache.spark.graphx.PartitionStrategy._
import java.io._
import scala.collection.mutable.HashMap
object HiggsDatasetGenerator {
  def main(args:Array[String]) = {
    val path  = args(0)
    val sparkMaster = args(1)  
    val output_path = args(2)
    val sparkConf = new SparkConf().setAppName("JabeJa : ").setMaster(sparkMaster)
    val sc = new SparkContext(sparkConf)
    var G : Array[Edge[Int]] = Array()
    val files = new File(path).list
    files.foreach(println)
    for(x<-0 to 3){
      val edges = sc.textFile(path+files(x), 3).map(line=>{
                  val edge = line.split(" ")
                  Edge(edge(0).toLong-1,edge(1).toLong-1,x)
      }).collect()
      G = G ++ edges
    }
    G = G.sortBy(x=>x.srcId)
    val writer = new PrintWriter(new File(output_path))
    G.foreach(v=>writer.write(v.srcId+" "+v.dstId+" "+v.attr+"\n"))
    writer.close()
  }
  
}