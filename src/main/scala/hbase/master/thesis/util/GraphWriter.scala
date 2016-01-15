package hbase.master.thesis.util
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark._
import org.apache.spark.graphx._
import scala.collection.mutable.HashMap
import scala.collection.immutable.HashSet
import org.apache.hadoop.hbase.client.HBaseAdmin
import org.apache.hadoop.hbase.HTableDescriptor
import org.apache.hadoop.hbase.HColumnDescriptor
import org.apache.hadoop.hbase.util.Bytes
import java.io._
import scala.io.Source
import unicredit.spark.hbase._
import org.apache.spark.rdd.RDD
import org.apache.spark.broadcast.Broadcast
object GraphWriter {
  var sparkMaster = ""
  var hbaseMaster = ""
  var path = ""
  var partitionFile = ""
  var tableName = "testgraph";
  implicit val config = HBaseConfig()
  def toHex(id : Int, partitionMap: Map[Int,Int]) : String = {
    ('a'.toInt+partitionMap.get(id).get).toChar+"%06d".format(id)
  }
  def writeGraph(sc:SparkContext):Unit = {
    val nodeMap = Source.fromFile(partitionFile)
                    .getLines()
                    .zipWithIndex
                    .map(v=>(v._1.toInt,v._2.toInt))
                    .toList
                    .groupBy(_._1)
                    .toSeq.sortBy(_._1)
                    .flatMap(v=>v._2.map(_._2).toList)
                    .zipWithIndex
                    .toMap
    val partitionMap = Source.fromFile(partitionFile)
                    .getLines()
                    .map(v=>v.toInt)
                    .zipWithIndex
                    .map(v=>(nodeMap.get(v._2.toInt).get,v._1))
                    .toMap[Int,Int]
                    
    val partitionNum = partitionMap.values.max+1
    println(partitionNum)
    val admin = new HBaseAdmin(config.get)
    if(false == admin.tableExists(tableName)){
        val splitKeys = List.range(1, partitionNum).map(v=>Bytes.toBytes(('a'.toInt+v).toChar+"000000"))
                                                    .toArray
        val tableDescriptor = new HTableDescriptor(Bytes.toBytes(tableName))
        tableDescriptor.addFamily(new HColumnDescriptor("to"))
        tableDescriptor.addFamily(new HColumnDescriptor("from"))
        tableDescriptor.addFamily(new HColumnDescriptor("property"))
        admin.createTable(tableDescriptor, splitKeys)
    }
    val rdd : RDD[(String, Map[String, String])] = sc.textFile(path, partitionNum).map(line=>{
                val edge = line.split(" ")
                ( (toHex(nodeMap.get(edge(0).toInt).get,partitionMap),edge(2)) 
                    ,toHex(nodeMap.get(edge(1).toInt).get,partitionMap) )
              } )
              .reduceByKey((a, b) => a+':'+b).map(v=>(v._1._1,(v._1._2,v._2)))
              .groupBy(_._1)
              .map(v=>(v._1,v._2.map(k=>k._2).toMap))
//              .cache
    rdd.toHBase(tableName, "to")
    val rdd1 : RDD[(String, Map[String, String])] = sc.textFile(path, partitionNum).map(line=>{
                val edge = line.split(" ")
                ( (toHex(nodeMap.get(edge(1).toInt).get,partitionMap),edge(2)) 
                    ,toHex(nodeMap.get(edge(0).toInt).get,partitionMap))
              } )
              .reduceByKey((a, b) => a+':'+b).map(v=>(v._1._1,(v._1._2,v._2)))
              .groupBy(_._1)
              .map(v=>(v._1,v._2.map(k=>k._2).toMap))
//              .cache
    rdd1.toHBase(tableName, "from")
    val col = Map(
      "inputnode" -> "true"
    )
    val inputnodeRdd = sc.textFile(path, partitionNum).map(line=>{
                val edge = line.split(" ")
                ( (nodeMap.get(edge(0).toInt).get,edge(2)) ,nodeMap.get(edge(1).toInt).get )
              } ).filter(v=>(partitionMap.get(v._1._1.toInt)!=partitionMap.get(v._2.toInt) ) )
              .map(v=>v._2).distinct().map(v=>(toHex(v.toInt,partitionMap),col))
              
    inputnodeRdd.toHBase(tableName, "property")
    
//    restnode.map(f=>(f,-1,-1)).saveToCassandra(keyspace, tableName, SomeColumns("srcid","label","dstid"))
    println("afterput")
  }
  def main(args:Array[String]) : Unit = {
    path  = args(0)
    partitionFile = args(1)
    tableName = "cgou:"+partitionFile.split("/").last
    val sparkConf = new SparkConf().setAppName("GraphWriter")
    val sc = new SparkContext(sparkConf)
    
    writeGraph(sc)
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