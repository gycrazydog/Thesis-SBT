name := "Thesis-hbase-spark"
organization := "TUDELFT"
version := "0.0.1"
scalaVersion := "2.10.5"
libraryDependencies += "eu.unicredit" %% "hbase-rdd" % "0.6.0"
libraryDependencies += "com.datastax.spark" %% "spark-cassandra-connector" % "1.2.1"
libraryDependencies += "org.apache.spark" %% "spark-core" % "1.5.0" % "provided"
libraryDependencies += "org.apache.spark" %% "spark-graphx" % "1.5.0" % "provided"
libraryDependencies += "org.apache.hbase" % "hbase-common" % "0.98.6-cdh5.3.1"
libraryDependencies += "org.apache.hbase" % "hbase-client" % "0.98.6-cdh5.3.1"
libraryDependencies += "org.apache.hbase" % "hbase-server" % "0.98.6-cdh5.3.1"
libraryDependencies += "org.json4s" %% "json4s-jackson" % "3.2.11"
libraryDependencies += "org.apache.hbase" % "hbase-hadoop-compat" % "0.98.8-hadoop2"
assemblyMergeStrategy in assembly := {
  case PathList("javax", "servlet", xs @ _*)         => MergeStrategy.first
  case PathList("org", "apache", xs @ _*)         => MergeStrategy.first
  case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.first
  case "application.conf"                            => MergeStrategy.concat
  case "unwanted.txt"                                => MergeStrategy.discard
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
resolvers ++= Seq(
  "Cloudera repos" at "https://repository.cloudera.com/artifactory/cloudera-repos",
  "Cloudera releases" at "https://repository.cloudera.com/artifactory/libs-release"
)
