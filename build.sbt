name := "Thesis-hbase-spark"
organization := "TUDELFT"
version := "0.0.1"
scalaVersion := "2.10.5"
libraryDependencies += "eu.unicredit" %% "hbase-rdd" % "0.6.0"
libraryDependencies += "com.datastax.spark" %% "spark-cassandra-connector" % "1.2.1"
libraryDependencies += "org.apache.spark" %% "spark-core" % "1.2.2" % "provided"
libraryDependencies += "org.apache.spark" %% "spark-graphx" % "1.2.2" % "provided"
libraryDependencies += "org.apache.hbase" % "hbase-common" % "0.98.6-cdh5.3.1"% "provided"
libraryDependencies += "org.apache.hbase" % "hbase-client" % "0.98.6-cdh5.3.1"% "provided"
libraryDependencies += "org.apache.hbase" % "hbase-server" % "0.98.6-cdh5.3.1"% "provided"
libraryDependencies += "org.json4s" %% "json4s-jackson" % "3.2.11" % "provided"
resolvers ++= Seq(
  "Cloudera repos" at "https://repository.cloudera.com/artifactory/cloudera-repos",
  "Cloudera releases" at "https://repository.cloudera.com/artifactory/libs-release"
)
