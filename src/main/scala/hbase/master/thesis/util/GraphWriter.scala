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
import unicredit.spark.hbase._
import org.apache.spark.rdd.RDD
object GraphWriter {
  var sparkMaster = ""
  var hbaseMaster = ""
  var path = ""
  var partitionFile = ""
  var tableName = "testgraph";
  implicit val config = HBaseConfig()
  var partitionMap : Map[Int,Int] = Map()
  def toHex(id : Int) : String = {
    ('a'.toInt+partitionMap.get(id).get).toChar+"%06d".format(id)
  }
  def writeAlibaba(sc:SparkContext):Unit = {
    val rdd : RDD[(String, Map[String, String])] = sc.textFile(path, 3).map(line=>{
                val edge = line.split(" ")
                ( (toHex(edge(0).toInt),edge(2)) ,toHex(edge(1).toInt))
              } )
              .reduceByKey((a, b) => a+':'+b).map(v=>(v._1._1,(v._1._2,v._2)))
              .groupBy(_._1)
              .map(v=>(v._1,v._2.map(k=>k._2).toMap))
              .cache
    rdd.toHBase("test", "to")
    val rdd1 : RDD[(String, Map[String, String])] = sc.textFile(path, 3).map(line=>{
                val edge = line.split(" ")
                ( (toHex(edge(1).toInt),edge(2)) ,toHex(edge(0).toInt))
              } )
              .reduceByKey((a, b) => a+':'+b).map(v=>(v._1._1,(v._1._2,v._2)))
              .groupBy(_._1)
              .map(v=>(v._1,v._2.map(k=>k._2).toMap))
              .cache
    rdd1.toHBase("test", "from")
    val col = Map(
      "inputnode" -> "true"
    )
    val inputnodeRdd = sc.textFile(path, 3).map(line=>{
                val edge = line.split(" ")
                ( (edge(0),edge(2)) ,edge(1) )
              } ).filter(v=>(partitionMap.get(v._1._1.toInt)!=partitionMap.get(v._2.toInt) ) )
              .map(v=>v._2).distinct().map(v=>(toHex(v.toInt),col))
              
    inputnodeRdd.toHBase("test", "property")
    
//    restnode.map(f=>(f,-1,-1)).saveToCassandra(keyspace, tableName, SomeColumns("srcid","label","dstid"))
    println("afterput")
  }
  def main(args:Array[String]) : Unit = {
    tableName = args(0)
    path  = args(1)
    partitionFile = args(2)
    sparkMaster = args(3)
    hbaseMaster = args(4)
    val sparkConf = new SparkConf().setAppName("GraphWriter").setMaster(sparkMaster)
                                    .set("spark.hbase.host", hbaseMaster)
    val sc = new SparkContext(sparkConf)
    partitionMap = sc.textFile(partitionFile)
                    .map(v=>v.toInt)
                    .zipWithIndex
                    .map(v=>(v._2.toInt,v._1))
                    .collect()
                    .toMap[Int,Int]
    println(toHex(52049))
    writeAlibaba(sc)
//    mapJabeJaGraph(sc)
////    rdd.foreachPartition(x=>println("partition size : ",x.size))
//    writeTest(sc)
//    setOutputNodes(sc)
//    setInputNodes(sc)
//    mapGraph(sc)
//    printMap(sc)
//    mapMetisGraph(sc)
//    createMitsGraph(sc)
//    mapCsvToTxt(sc)
  }
}