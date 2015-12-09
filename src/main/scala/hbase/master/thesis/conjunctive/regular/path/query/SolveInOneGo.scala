package hbase.master.thesis.conjunctive.regular.path.query
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
import hbase.master.thesis.util.GraphReader
object SolveInOneGo {
  var path = "";
  var tableName = "testgraph";
  var keyspace = "";
  implicit val config = HBaseConfig(
    "hbase.rootdir" -> "hdfs://hadoop-m:8020/hbase",
    "hbase.zookeeper.quorum" -> "hadoop-m"
  )
  case class State(startid : Long,srcid : Long, edge : Edge[String])
   def run(sc:SparkContext,workerNum:Int):Set[((String,String),Long)] = {
    println("------------------------------start"+path+"--------------------------")
    val auto = GraphReader.automata(sc,path)
    val automata = auto.edges
    val finalState = HashSet(auto.vertices.count().toLong)
    val startTime = System.currentTimeMillis 
    var ans : Set[((String,String),VertexId)] = new HashSet()
    var currentTrans = automata.filter(e=>e.srcId==1L)
    val labelset = currentTrans.collect.map(v=>v.attr).toSet
    val columns = Map(
      "to"   -> labelset    
    )
    val labelcount = currentTrans.count()
    val startEdges = sc.hbase[String](tableName,columns)
                        .flatMap(v=>v._2.values.map(k=>(v._1,k)))
                        .flatMap(v=>v._2.map(k=>(v._1,k._1,k._2)))
                        .flatMap(v=>v._3.split(":").map(k=>(v._2,(v._1,k))))
    startEdges.collect().foreach(println("start state : ",_))
    //RDD[((edge.dstid,autoedge.dstid),(edge.startid,autoedge.lastid))
    var currentStates : RDD[((String,VertexId),(String,VertexId))] = startEdges
                    .join(currentTrans.map(e=>(e.attr,e))) 
                    .map(f=>((f._2._1._2,f._2._2.dstId),(f._2._1._1,f._2._2.srcId)))
                    .cache()
      var size = currentStates.count()
      var visitedStates : RDD[((String,VertexId),(String,VertexId))] = sc.emptyRDD
      var i = 0
      while(size>0){
//        currentStates.collect().foreach(println("current state : ",_))
        val nextTrans = currentTrans.map(v=>(v.dstId,v))
                        .join(automata.map(v=>(v.srcId,v)))
                        .map(v=>v._2._2)
                        .distinct
        currentTrans = nextTrans
        val nextTotalStates = visitedStates.union(currentStates).coalesce(workerNum)
        visitedStates = nextTotalStates
//        println("CUrrent Partitions : "+currentStates.partitions.length)
//        println("Current States : "+nextTotalStates.count())
//        println("Current Trans : "+currentTrans.length)
        i = i+1
        println("iteration:"+i)
        println("current States size :"+size)   
        //        currentStates.collect.foreach(v=>println("current State : "+v))
        ans = ans ++ currentStates.filter(v=>finalState.contains(v._1._2)).map(v=>((v._2._1,v._1._1),v._2._2 ) ).collect()
        val labelset = currentTrans.map(v=>v.attr).distinct().collect().toSet
        val columns = Map(
            "to"   -> labelset    
        )
        println("Answer Size : "+ans.size)
        //currentStates : RDD[((edge.dstid,autoedge.dstid),(edge.startid,autoedge.lastid))
         //nextEdges : RDD[((edge.srcid,autoedge.srcid),(edge.dstid,autoedge.dstid))
        val nextEdges = sc.hbase[String](tableName, columns)
                       .flatMap(v=>v._2.values.map(k=>(v._1,k)))
                       .flatMap(v=>v._2.map(k=>(v._1,k._1,k._2)))
                       .flatMap(v=>v._3.split(":").map(k=>(v._2,(v._1,k))))
                       .join(currentTrans.map(e=>(e.attr,e)))
                       .map(f=>((f._2._1._1,f._2._2.srcId),(f._2._1._2,f._2._2.dstId)))
        val nextStates = nextEdges
                        .join(currentStates)
                        .map(v=>(v._2._1,(v._2._2._1,v._1._2) ))
                        .subtract(visitedStates)
                        .cache()
//        println("iteration : "+i+ " count : "+nextStates.collect())
        currentStates = nextStates
        size = currentStates.count()
        println("finishing calculating currentStates!")
      }
      ans.foreach(println("ahahaha ",_))
      val endTime = System.currentTimeMillis
      val finalAns = ans.groupBy(_._1).map(s=>(s._1,s._2.map(v=>v._2))).filter(s=>s._2.size==automata.filter(a=>finalState.contains(a.dstId)).count)
      finalAns.map(v=>println("pair found : "+v))
      println("number of pairs : "+finalAns.size)
      println("time : "+(endTime-startTime))
      println("-------------------------------------------------------------")
      ans.filter(a=>finalAns.contains(a._1))
  }
  def main(args:Array[String]){
    val sparkConf = new SparkConf().setAppName("conjunctive solve and merge : ").setMaster("local[3]")
    val sc = new SparkContext(sparkConf)
    path = args(0)
    tableName = args(1)
    val ans = run(sc,3)
  }
}