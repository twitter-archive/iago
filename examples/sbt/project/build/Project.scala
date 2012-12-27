import sbt._
import com.twitter.sbt._

class Project(info: ProjectInfo) extends StandardServiceProject(info)
  with PackageDist
  with CompileThriftFinagle
  with DefaultRepos {

  //Assumes you are using SBT for iago as well, and that it's in ivy
  // (ex. ~/.ivy2/local/com/twitter/iago/0.5.1/iago-0.5.1.jar)

  val libThrift = "thrift" % "libthrift" % "0.5.0"
  val finagleCore = "com.twitter" % "finagle-core" % "5.3.0"
  val finagleServerSets = "com.twitter" % "finagle-serversets" % "5.3.0"
  val finagleMemcached = "com.twitter" % "finagle-memcached" % "5.3.0"
  val finagleStream = "com.twitter" % "finagle-stream" % "5.3.0"
  val finagleThrift = "com.twitter" % "finagle-thrift" % "5.3.0"
  val finagleOstrich = "com.twitter" % "finagle-ostrich4" % "5.3.0"
  val iago = "com.twitter" % "iago" % "0.5.1"
  val ostrich = "com.twitter" % "ostrich" % "8.0.1"
  val utilThrift = "com.twitter" % "util-thrift" % "5.3.0"
  def customRun(mainClass: String, args: String*) = task { _ =>
    runTask(Some(mainClass), runClasspath, args) dependsOn(compile, copyResources)
  }

  lazy val server = customRun("com.twitter.example.EchoServer")
  lazy val client = customRun("com.twitter.example.EchoClient")

  lazy val startParrot = customRun("com.twitter.parrot.launcher.LauncherMain", "-f", "config/echo.scala")
  lazy val killParrot = customRun("com.twitter.parrot.launcher.LauncherMain", "-f", "config/echo.scala", "-k")

  override def mainClass = Some("com.twitter.parrot.launcher.LauncherMain")
  override def ivyXML =
    <dependencies>
      <exclude org="javax.jms"/>
      <exclude org="com.sun.jdmk"/>
      <exclude org="com.sun.jmx"/>
    </dependencies>
}
