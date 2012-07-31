import sbt._
import scala.collection.jcl

class Plugins(info: ProjectInfo) extends PluginDefinition(info) {
  val environment = jcl.Map(System.getenv())

  override def repositories = super.repositories++ Seq("twitter.com" at "http://maven.twttr.com/")
  
  override def ivyRepositories = super.ivyRepositories ++ repositories

    val defaultProject = "com.twitter" % "standard-project" % "0.12.10"
    val sbtThrift      = "com.twitter" % "sbt-thrift" % "1.4.2"
}