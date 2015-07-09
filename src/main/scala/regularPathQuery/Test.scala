package regularPathQuery
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark._
import org.apache.spark.graphx._
import com.datastax.spark.connector._
// To make some of the examples work we will also need RDD
import org.apache.spark.rdd.RDD
import org.apache.spark.graphx.PartitionStrategy._
object Test {
  case class EdgeLabel(label: String)
  case class SrcId(srcid : Long)
  def main(args : Array[String]){
    val sparkConf = new SparkConf().setAppName("SparkShuffleTest-GroupByKey").setMaster("spark://ubuntu:7077")
      val sc = new SparkContext(sparkConf)
      val ids = sc.parallelize(1 to 300000).map(v=>(v,1))
      println(ids.groupByKey().keys.count())
  }
}
