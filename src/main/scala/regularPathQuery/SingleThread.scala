package regularPathQuery
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark._
import org.apache.spark.graphx._
import com.datastax.spark.connector._
import scala.collection.immutable.HashSet
// To make some of the examples work we will also need RDD
import org.apache.spark.rdd.RDD
import org.apache.spark.graphx.PartitionStrategy._
object SingleThread {
  def singleThread():Unit = {
//      val sparkConf = new SparkConf().setAppName("HBaseMultipleThread").setMaster("local")
//      .set("spark.cassandra.connection.host", "127.0.0.1")
//      val sc = new SparkContext(sparkConf)
//      println("start !!!")
//      val automata = GraphReader.automata(sc).edges
//      val finalState = HashSet(3L)
//      val startTime = System.currentTimeMillis 
//      var ans : Array[VertexId] = Array()
//      var currentTrans = automata.filter(e=>e.srcId==1L)
//      var currentStates : RDD[(VertexId,VertexId)] = sc.parallelize(Array(1L)).cartesian(sc.parallelize(1L to GraphReader.getGraphSize(sc), 3))
//      currentStates.cache()
//      var visitedStates : HashSet[(VertexId,VertexId)] = HashSet()
//      var i = 0
//      while(currentStates.count()>0){
//        visitedStates ++= currentStates.collect()
//        println("visited States : "+visitedStates.size)
//        i = i+1
//        println("iteration:"+i)
////        println("current States:")
//        ans = ans ++ currentStates.filter(v=>finalState.contains(v._1)).map(v=>v._2).collect()
//        currentStates = GraphReader.getNextStates(sc, currentStates.cartesian(currentTrans).filter(f=>f._1._1==f._2.srcId).map(f=>(f._1._2,f._2.attr)), currentTrans,visitedStates)
//        currentStates.cache()
//        currentTrans = currentTrans.cartesian(automata).filter(v=>v._1.dstId==v._2.srcId).map(v=>v._2)
//        println("finishing calculating currentStates!")
//      }
//      val endTime = System.currentTimeMillis
//      ans.map(v=>println("vertex reached!!! "+v))
//      println("time : "+(endTime-startTime))
    }
    def main(args:Array[String]){
      singleThread
    }
}