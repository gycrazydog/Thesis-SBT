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
import hbase.master.thesis.util.SolveLagrangean
object MultiWayJoin {
  var path = "";
  var tableName = "testgraph";
  implicit val config = HBaseConfig(
    "hbase.rootdir" -> "hdfs://hadoop-m:8020/hbase",
    "hbase.zookeeper.quorum" -> "hadoop-m"
  )
  def genKeys(curLevel : Int,buckets : Array[Int],curKeys : Array[Int], varNum : Int) : Array[Array[Int]] = {
    var temp : List[Array[Int]]= List()
    if(curLevel < varNum){
      for(i <- 0 to buckets(curLevel)-1){
        val nextKeys = curKeys :+ i
        temp ++= genKeys(curLevel+1,buckets,nextKeys,varNum)
      }
    }
    else{
      temp :+= curKeys
    } 
//    println(curLevel + " "+ temp.size )
    temp.toArray
  }
  def run(sc:SparkContext,workerNum:Int):Set[(String,String)] = {
      println("------------------------------start"+path+"--------------------------")
    val auto = GraphReader.automata(sc,path)
    val automata = auto.edges
    val vertices = auto.vertices.count()
    val finalState = HashSet(vertices)
    val startTime = System.currentTimeMillis 
    var ans : Set[(String,String)] = new HashSet()
    val labelset = automata.collect.map(v=>v.attr).toSet
    val columns = Map(
      "to"   -> labelset    
    )
    //RDD[((edge.dstid,autoedge.dstid),(edge.srcid,autoedge.srcid))]
    val startedges = sc.hbase[String](tableName, columns)
                       .flatMap(v=>v._2.values.map(k=>(v._1,k)))
                       .flatMap(v=>v._2.map(k=>(v._1,k._1,k._2)))
                       .flatMap(v=>v._3.split(":").map(k=>(v._2,(v._1,k))))
                       .cache()
    val realAutos = automata.filter(e=>e.srcId!=e.dstId).cache()
    val selfAutos = automata.filter(e=>e.srcId==e.dstId).cache()
    val preSelfAutos = realAutos.map(e=>(e.dstId,e)).join(selfAutos.map(e=>(e.srcId,e)))
                                .map(f=>f._2._1)                
    val selfStates =  startedges.join(selfAutos.map(e=>(e.attr,e)))
                        .map(f=>( (f._2._1._1,f._2._2.srcId), (f._2._1._2,f._2._2.dstId)))
    var baseStates = startedges.join(preSelfAutos.map(e=>(e.attr,e)))
                      .map(f=>((f._2._1._2,f._2._2.dstId),(f._2._1._1,f._2._2.srcId)))
    
    var currentStates = startedges.join(realAutos.map(e=>(e.attr,e)))
                        .map(f=>((f._2._1._2,f._2._2.dstId),(f._2._1._1,f._2._2.srcId)))
                        .repartition(workerNum)
                        .cache()
    while(baseStates.count()>0){
      val nextBase = baseStates.join(selfStates)
                                .map(v=>v._2)
                                .map(v=>(v._2,v._1))
                                .subtract(currentStates)
                                .distinct()
                                .cache()
      baseStates = nextBase
      val nextStates = currentStates.union(baseStates).repartition(workerNum)
      currentStates = nextStates
    }
    // no self loop anymore
    val rs = currentStates.map(f=>( (f._2._2,f._1._2),1 )  ).reduceByKey(_+_).collect()
                          .sortBy(f=>f._1._1)
                          .map(f=>f._2)
    rs.foreach(println("rs : ",_))
    val as = SolveLagrangean.solve(rs, 64)
    as.foreach(println("as : ",_))
    val keys = genKeys(0,as,Array(),as.size)
    val newStates = currentStates.flatMap(v=>{
      var var1 = v._1._2.toInt-1
      var var2 = v._2._2.toInt-1
      keys.filter(key=>key(var1)==v._1._1.hashCode()%as(var1)&&key(var2)==v._2._1.hashCode()%as(var2))
      .map(key=>(key.mkString("+"),List(v)))
    }).reduceByKey((a,b)=>a++b).cache()
    println("size of newStates : "+newStates.count())
    ans = newStates.map(v=>{
      var tempans : HashSet[(String,String)] = new HashSet()
      val masterStates = v._2.toArray
      val nextMasterStates = masterStates.map(v=>(v._2,v._1)).groupBy(_._1).mapValues(f=>f.map(_._2))
      var current = masterStates.filter(k=>k._2._2==1L)
      while(current.size>0){
        var visited : HashSet[((String,VertexId),(String,VertexId))] = new HashSet()
        visited = visited ++ current
//        println("current : ",current.size)
  //      current.foreach(println("state ",_))
        val stopStates = current.filter(p=>p._2._2==1L&&finalState.contains(p._1._2))
        tempans = tempans ++ stopStates.map(f=>(f._2._1,f._1._1))
        val nextCurrent = current.filter(v=>nextMasterStates.contains(v._1))
                          .flatMap(v=>nextMasterStates.get(v._1).get.map((_,v._2)))
                          .filter(visited.contains(_)==false)                          
        current = nextCurrent
      }
      tempans
    }).collect().flatten.toSet
    val endTime = System.currentTimeMillis
//      ans.map(v=>println("vertex reached!!! "+v))
    println("number of pairs : "+ans.size)
    println("time : "+(endTime-startTime))
    println("-------------------------------------------------------------")
    ans
  }
  def main(args:Array[String]){
      path = args(0)
      tableName = args(1)
      val sparkMaster = args(2)
      val sparkConf = new SparkConf().setAppName("Multiway Join : "+path).setMaster(sparkMaster)
      val sc = new SparkContext(sparkConf)
      val asn = run(sc,args(3).toInt)
  }
}