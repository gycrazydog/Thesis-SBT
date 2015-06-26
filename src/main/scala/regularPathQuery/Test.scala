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
  def main(args:Array[String]) = {
    val sparkConf = new SparkConf().setAppName("HBaseMultipleThread").setMaster("local[4]")
    .set("spark.cassandra.connection.host", "127.0.0.1")
    val sc = new SparkContext(sparkConf)
    val oddIds = sc.parallelize(Array(17L,1L))
     val labelset = "('video','category')"
      sc.cassandraTable("mykeyspace", "testgraph")
      .select("srcid", "label")
      .foreachPartition { x => println(x.size) }
  }
}
