package regularPathQuery
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark._
import org.apache.spark.graphx._
import com.datastax.spark.connector._
import scala.collection.immutable.HashSet
import scala.collection.mutable.HashMap
// To make some of the examples work we will also need RDD
import org.apache.spark.rdd.RDD
import org.apache.spark.graphx.PartitionStrategy._
object DanAlgorithm {
  case class SrcId(srcid : Long) 
  case class Complex(srcid: Long,auto: Edge[String],edge: Edge[String])
  var path = "";
  var tableName = "testgraph";
  var keyspace = "";
  def run(workerNum:Int):Unit = {
    val sparkConf = new SparkConf().setAppName("DanAlgorithm : "+path)
		.setMaster("spark://ubuntu:7077")      
		.set("spark.cassandra.connection.host", "127.0.0.1")
    println("------------------------------start"+path+"--------------------------")
    val sc = new SparkContext(sparkConf)
    //val nodes = sc.parallelize( 1 to 26, 3)
    var masterStates : HashSet[(Edge[String],(Long,Long))] = new HashSet()
    val initialNodes = Array(17L)
    val auto = GraphReader.automata(sc,path)
    val automata = auto.edges.collect()
    var nextAuto: HashMap[VertexId,Array[Edge[String]]] = new HashMap()
    val am = auto.edges.map(v=>(v.srcId,v)).collect()
    am.map(f=>{
        if(nextAuto.contains(f._1))
        nextAuto += (f._1 -> (nextAuto.get(f._1).get:+f._2) )
        else
        nextAuto += (f._1 -> Array(f._2) )  
    })
    val finalState = HashSet(auto.vertices.count().toLong)
    var currentTrans = automata.filter(e=>e.srcId==1L)
    val labelset = "("+currentTrans.map(v=>"'"+v.attr+"'").mkString(",")+")"
    val currentNodes = sc.cassandraTable(keyspace, tableName).where("inputnode = true")
                          .union(sc.cassandraTable(keyspace, tableName).where("label IN "+labelset))
                          .coalesce(3)
    var currentStates = currentNodes.flatMap(x=>automata.map(v=>(v,x)) )
                                      .filter(v=>(v._1.attr==v._2.getString("label")))
                                      .flatMap(f=>f._2.getString("dstid").split(":")
                                      .map(k=>(f._1,(f._2.getInt("srcid").toLong,f._2.getString("label"),k))))
                                      .distinct()
                                      .cache()
    //currentStates.collect().foreach(println("init state : ",_))
    var visitedStates : RDD[(Edge[String],(Long,String,String))] = sc.emptyRDD
    var size = currentStates.count()
    var i = 0
    while(size>0){
      val nextTotalStates = visitedStates.union(currentStates).coalesce(3)
      visitedStates = nextTotalStates
      i = i+1
      println("iteration:"+i)
      println("currentStates : ",size)
        masterStates = masterStates ++ currentStates.filter(f=>f._2._3.split("-")(1).toInt==0 
                                                            || finalState.contains(f._1.dstId)).collect()
                                                            .map(f=>(f._1,(f._2._1,f._2._3.split("-")(0).toLong)))
                                                            
                                                    
        val nextStates = currentStates.filter(f=>f._2._3.split("-")(1).toInt==1 
                                                            && false==finalState.contains(f._1.dstId) )
                                       .map(f=>Complex(f._2._3.split("-")(0).toLong,f._1,Edge(f._2._1,f._2._3.split("-")(0).toLong,f._2._2)))
                                       .joinWithCassandraTable(keyspace, tableName)
                                       .map(f=>{
                                         val temp = nextAuto.get(f._1.auto.dstId).get
                                                             .filter(x=>x.attr==f._2.getString("label"))
                                         if(temp.length>0)
                                           (Edge(f._1.auto.srcId,temp(0).dstId,temp(0).attr)
                                                ,(f._1.edge.srcId,f._2.getString("label"),f._2.getString("dstid")))
                                         else
                                           (null,(f._1.srcid,f._2.getString("label"),f._2.getString("dstid")))
                                       })
                                       .filter(f=>f._1!=null)
                                       .flatMap(f=>f._2._3.split(":")
                                       .map(k=>(f._1,(f._2._1,f._2._2,k))))
                                       .distinct()
                                       .subtract(visitedStates)
                                       .cache()
        currentStates = nextStates
        size = currentStates.count()
//      val nextGlobalMatches = visitedStates.union(currentStates)
//      visitedStates = nextGlobalMatches
//      val nextStates = currentStates.
    }
    println("masterStates : ",masterStates.size)
    var ans : HashSet[(VertexId,VertexId)] = new HashSet()
    var visited : HashSet[(Edge[String],(Long,Long))] = new HashSet()
    var current = masterStates.filter(p=>p._1.srcId==1L)
    while(current.size>0){
      visited = visited ++ current
      val stopStates = current.filter(p=>p._1.srcId==1L&&finalState.contains(p._1.dstId))
      ans = ans ++  stopStates.map(f=>f._2)
      var nextAns = current.flatMap(s=>{
      var temp: HashSet[(Edge[String],(Long,Long))] = new HashSet()
        masterStates.map(t=>{
          if(s._1.dstId==t._1.srcId&&s._2._2==t._2._1)
            temp += ( (Edge(s._1.srcId,t._1.dstId,t._1.attr), (s._2._1,t._2._2)) )
        })
        temp
      } ).filter(visited.contains(_)==false)
                                  
      current = nextAns
    }
    println("ans size : ",ans.size)
     ans.foreach(println)
    println("---------------------------------------------------------------------")
  }
  def main(args:Array[String]) = {
    path = args(0)
    tableName = args(2)
    keyspace = args(1)
    run(3)
  }
  
}
