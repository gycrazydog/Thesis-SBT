package hbase.master.thesis.regular.path.query
import unicredit.spark.hbase._
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark._
import org.apache.spark.graphx._
import scala.collection.immutable.HashSet
// To make some of the examples work we will also need RDD
import org.apache.spark.rdd.RDD
import org.apache.spark.graphx.PartitionStrategy._
import hbase.master.thesis.util.GraphReader
object TwoWayJoin {
  var path = "";
  var tableName = "testgraph";
  implicit val config = HBaseConfig(
    "hbase.rootdir" -> "hdfs://hadoop-m:8020/hbase",
    "hbase.zookeeper.quorum" -> "hadoop-m"
  )
  def run(sc:SparkContext,workerNum:Int):Set[(String,String)] = {
      println("------------------------------start"+path+"--------------------------")
    val auto = GraphReader.automata(sc,path)
    val automata = auto.edges
    val finalState = HashSet(auto.vertices.count().toLong)
    val startTime = System.currentTimeMillis 
    var ans : Set[(String,String)] = new HashSet()
    var currentTrans = automata.filter(e=>e.srcId==1L)
    val labelset = currentTrans.collect.map(v=>v.attr).toSet
    val columns = Map(
      "to"   -> labelset    
    )
    //RDD[((edge.dstid,autoedge.dstid),edge.startid)]
    val startedges = sc.hbase[String](tableName, columns)
                       .flatMap(v=>v._2.values.map(k=>(v._1,k)))
                       .flatMap(v=>v._2.map(k=>(v._1,k._1,k._2)))
                       .flatMap(v=>v._3.split(":").map(k=>(v._2,(v._1,k))))
    var currentStates =  startedges.join(currentTrans.map(e=>(e.attr,e))) 
                         .map(f=>((f._2._1._2,f._2._2.dstId),f._2._1._1))
                         .cache()
      var size = currentStates.count()
      var visitedStates : RDD[((String,VertexId),String)] = sc.emptyRDD
      var i = 0
      while(size>0){
//        currentStates.collect().foreach(println("current state : ",_))
        val nextTotalStates = visitedStates.union(currentStates).coalesce(workerNum)
        visitedStates = nextTotalStates
        val nextTrans = currentTrans.map(v=>(v.dstId,v))
                        .join(automata.map(v=>(v.srcId,v)))
                        .map(v=>v._2._2)
                        .distinct
        currentTrans = nextTrans
//        println("CUrrent Partitions : "+currentStates.partitions.length)
//        println("Current States : "+nextTotalStates.count())
//        println("Current Trans : "+currentTrans.length)
        i = i+1
        println("iteration:"+i)
        println("current States size :"+size)   
        //        currentStates.collect.foreach(v=>println("current State : "+v))
        ans = ans ++ currentStates.filter(v=>finalState.contains(v._1._2)).map(v=>(v._2,v._1._1)).collect()
        val labelset = currentTrans.collect.map(v=>v.attr).toSet
        println("Answer Size : "+ans.size)
        //RDD[((edge.dstid,autoedge.dstid),edge.startid)]
        //RDD[((edge.srcid,autoedge.srcid),(edge.dstid,autoedge.dstid))]
        val columns = Map(
            "to"   -> labelset    
        )
        var nextEdges : RDD[((String,VertexId),(String,VertexId))] = sc.hbase[String](tableName, columns)
                       .flatMap(v=>v._2.values.map(k=>(v._1,k)))
                       .flatMap(v=>v._2.map(k=>(v._1,k._1,k._2)))
                       .flatMap(v=>v._3.split(":").map(k=>(v._2,(v._1,k))))
                       .join(currentTrans.map(e=>(e.attr,e)))
                       .map(f=>((f._2._1._1,f._2._2.srcId),(f._2._1._2,f._2._2.dstId)))
        val nextStates = nextEdges
                        .join(currentStates)
                        .map(v=>v._2)
                        .subtract(visitedStates)
                        //.filter(!visitedStates.contains(_))
                        .distinct()
//        println("iteration : "+i+ " count : "+nextStates.collect())
        currentStates = nextStates.cache
        size = currentStates.count()
        println("finishing calculating currentStates!")
      }
      val endTime = System.currentTimeMillis
      ans.map(v=>println("vertex reached!!! "+v))
      println("number of pairs : "+ans.size)
      println("time : "+(endTime-startTime))
      println("-------------------------------------------------------------")
      ans
  }
  def main(args:Array[String]){
      path = args(0)
      tableName = args(1)
      val sparkMaster = args(2)
      val sparkConf = new SparkConf().setAppName("Two Way Join : "+path).setMaster(sparkMaster)
      val sc = new SparkContext(sparkConf)
      val asn = run(sc,args(3).toInt)
  }
}