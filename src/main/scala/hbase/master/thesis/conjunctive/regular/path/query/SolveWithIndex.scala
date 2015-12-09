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
import scala.collection.mutable.BitSet
import java.io._
import scala.io.Source
import scala.collection.mutable.Queue
import org.apache.hadoop.hbase.client.Scan
object SolveWithIndex {
  var path = "";
  var tableName = "testgraph";
  var keyspace = "";
  var maxLevel = 0
  var tree : Array[(Int,List[Vertex])] = Array.empty
  var levels = 0
  var nodes = 0
  implicit val config = HBaseConfig(
    "hbase.rootdir" -> "hdfs://hadoop-m:8020/hbase",
    "hbase.zookeeper.quorum" -> "hadoop-m"
  )
  case class SrcId(srcid : Long) 
  class Vertex(
    var id : Long,
    var vertexSig : BitSet
  )
  def toHex(id : Int) : String = {
    ('a'.toInt+(id/(nodes/3))).toChar+"%06d".format(id)
  }
  def loadIndex(){
    val files = new File("/home/crazydog/workspace/Spark test/vs-tree/").list
    levels = files.size-1
    val temp = files.map(s=>(s.split("\\.")(0).toInt,s)).filter(_._1>=maxLevel)
    tree = temp.map(n=>{
      val lines = Source.fromFile("/home/crazydog/workspace/Spark test/vs-tree/"+n._2).getLines()
      val vertices = lines.map(l=>{
          var bitSet = BitSet.empty
          val values = l.split(" ")(1).split(",").map(v=>v.toInt)
          values.foreach(v=>bitSet+=v)
          new Vertex(l.split(" ")(0).toLong,bitSet)
      }).toList
      (n._1,vertices)
    }).sortBy(_._1).reverse
//    println("size of tree is " +SizeEstimator.estimate(tree))
//    tree.foreach(v=>v._2.foreach(k=>println(v._1+" "+k.id+" "+k.vertexSig.size)))
  }
  def lookupIndex(querySig : BitSet) : List[(Long,Long)] = {
    var ans : List[(Long,Long)] = List()
    var vQueue = Queue[(Int,Vertex)]((0,tree(0)._2(0)))

    while(vQueue.size>0){
      val head = vQueue.dequeue()
      if((querySig&head._2.vertexSig).equals(querySig)){
        if(head._1+1<tree.size){
          val left = tree(head._1+1)._2(head._2.id.toInt*2)
          vQueue+= ( ((head._1+1),left) )
          if(head._2.id.toInt*2+1<tree(head._1+1)._2.size){
            val right = tree(head._1+1)._2(head._2.id.toInt*2+1)
            vQueue+= ( ((head._1+1),right) )
          }
        }
        else{
//          println(head._2.id*(1<<(levels-head._1))+" "+(head._2.id+1)*(1<<(levels-head._1)))
//          println(querySig&head._2.vertexSig)
          ans = ans :+ ( head._2.id*(1<<(levels-head._1)),(head._2.id+1)*(1<<(levels-head._1)) )
        }
      }
    }
    ans
  }
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
    var startSet = BitSet()
    currentTrans.collect.foreach(startSet+=_.attr.toInt)
    val intervals = lookupIndex(startSet)
    println("startnodes size : "+intervals.size)
    intervals.foreach(println("interval :",_))
    var startEdges : RDD[(String,(String,String))] = sc.emptyRDD
    intervals.foreach(v=>{
      val scan = new Scan()
      scan.setStartRow(toHex(v._1.toInt))
      scan.setStopRow(toHex(v._2.toInt))
      val tempEdges = sc.hbase[String](tableName,columns,scan)
                        .flatMap(v=>v._2.values.map(k=>(v._1,k)))
                        .flatMap(v=>v._2.map(k=>(v._1,k._1,k._2)))
                        .flatMap(v=>v._3.split(":").map(k=>(v._2,(v._1,k))))
      val nextstartedges = startEdges.union(tempEdges)
      startEdges = nextstartedges
    })
    startEdges.repartition(3)
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
        ans = ans ++ currentStates.filter(v=>finalState.contains(v._1._2)).map(v=>((v._2._1,v._1._1),v._2._2)).collect()
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
      .set("spark.eventLog.enabled ","true")
    val sc = new SparkContext(sparkConf)
    path = args(0)
    tableName = args(1)
    nodes =  sc.hbase(tableName).count.toInt
    loadIndex
//    println(sc.parallelize(tree.map(f=> (f._1,f._2.map(v=>(v.id,v.vertexSig) ) ) ), 3).count)
    val ans = run(sc,3)
  }
}