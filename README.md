<a name="Top"></a>

# Iago, A Load Generator
[![Build Status](https://secure.travis-ci.org/twitter/iago.png)](http://travis-ci.org/twitter/iago)

* <a href="#Iago Quick Start">Iago Quick Start</a>
  - <a href="#Iago Prerequisites">Iago Prerequisites</a>
  - <a href="#Preparing Your Test">Preparing Your Test</a>
  - <a href="#Executing Your Test">Executing Your Test</a>
* <a href="#Iago Overview">Iago Overview</a>
  - <a href="#Supported Services">Supported Services</a>
  - <a href="#Transaction Requirements">Transaction Requirements</a>
  - <a href="#Sources of Transactions">Sources of Transactions</a>
* <a href="#Iago Architecture Overview">Iago Architecture Overview</a>
* <a href="#Implementing Your Test">Implementing Your Test</a>
  - <a href="#Scala Example">Scala Example</a>
  - <a href="#Scala Thrift Example">Scala Thrift Example</a>
  - <a href="#Java Example">Java Example</a>
  - <a href="#Java Thrift Example">Java Thrift Example</a>
  - <a href="#Code Annotations for the Examples">Code Annotations for the Examples</a>
* <a href="#Configuring Your Test">Configuring Your Test</a>
	- <a href="#Specifying_Victims">Specifying Victims</a>
	- <a href="#extension_point_parameters">Extension Point Parameters</a>
	- <a href="#sending_large_messages">Sending Large Messages</a>
* <a href="#weighted_requests">Weighted Requests</a>
* <a href="#metrics">Metrics</a>
* <a href="#tracing">Tracing</a>
* <a href="#artifacts">What Files Are Created?</a>
* <a href="#ChangeLog">ChangeLog</a>
* <a href="#Contributing">Contributing to Iago</a>

<a name="Iago Quick Start"></a>

## Iago Quick Start

Please join [iago-users@googlegroups.com](https://groups.google.com/d/forum/iago-users) for updates and to ask questions.

If you are already familiar with the Iago Load Generation tool, follow these steps to get started; otherwise, start with the <a href="http://twitter.github.com/iago/">Iago Overview</a> and perhaps <a href="http://twitter.github.com/iago/philosophy.html">Iago Philosophy</a>, also known as "Why Iago?". For questions, please contact [iago-users@googlegroups.com](https://groups.google.com/d/forum/iago-users).

<a name="Iago Prerequisites"></a>

### Iago Prerequisites

1. Download and unpack the Iago distribution.
We support Scala 2.9 and recommend you clone the latest master: <a href="https://github.com/twitter/iago/zipball/master">master</a>.

2. Read the documentation.

<a name="Preparing Your Test"></a>

### Preparing Your Test

1. Identify your transaction source; see <a href="#Transaction Requirements">Transaction Requirements</a> and <a href="#Sources of Transactions">Sources of Transactions</a> for more information.
2. In Scala, extend the Iago server's `RecordProcessor` or `ThriftRecordProcessor` class, or in Java, extend `LoadTest` or `ThriftLoadTest`; see <a href="#Implementing Your Test">Implementing Your Test</a> for more information.
3. Create a `launcher.scala` file in your Iago `config` directory with the appropriate settings; see <a href="#Configuring Your Test">Configuring Your Test</a> for more information.

<a name="Executing Your Test"></a>

### Executing Your Test

Launch Iago from the distribution with `java` `-jar` *iago_jar* `-f` *your_config*. This will create the Iago processes for you and configure it to use your transactions. To kill a running job, add `-k` to your launch parameters: `java` `-jar` *iago_jar* `-f` *your_config* `-k`.

If you launch your Iago job on your local machine and an old Iago job is still running, it probably won't get far: it will attempt to re-use a port and fail. You want to kill the running job, as described above.

<em>If you build via Maven,</em> then you might wonder "How do I launch Iago 'from the distribution'?" The steps are:
<pre>
% <kbd>mvn package -DskipTests</kbd>
% <kbd>mkdir tmp; cd tmp</kbd>
% <kbd>unzip ../target/iago-<var>version</var>-package-dist.zip</kbd>
% <kbd>java -jar iago-<var>version</var>.jar -f config/<var>my_config</var>.scala</kbd>
</pre>
Don't assume that you can skip the package/unzip steps if you're just changing a config file. You need to re-package and unzip again.

If you are using Iago as a library, for example, in the case of testing over the Thrift protocol or building more complex tests with HTTP or Memcached/Kestrel, you should instead add a task to your project's configuration. See <a href="#Configuring Your Test">Configuring Your Test</a> for more information.

[Top](#Top)

<a name="Iago Overview"></a>

## Iago Overview

Iago is a load generation tool that replays production or synthetic traffic against a given target. Among other things, it differs from other load generation tools in that it attempts to hold constant the transaction rate. For example, if you want to test your service at 100K requests per minute, Iago attempts to achieve that rate.

Because Iago replays traffic, you must specify the source of the traffic. You use a transaction log as the source of traffic, in which each transaction generates a _request_ to your service that your service processes.

Replaying transactions at a fixed rate enables you to study the behavior of your service under an anticipated load. Iago also allows you to identify bottlenecks or other issues that may not be easily observable in a production environment in which your maximum anticipated load occurs only rarely.

[Top](#Top)

<a name="Supported Services"></a>

### Supported Services

Iago can generate service requests that travel the net in different ways and are in different formats. The code that does this is in a Transport, a class that extends <code>ParrotTransport</code>. Iago comes with several Transports already defined. When you configure your test, you will need to set some parameters; to understand which of those parameters are used and how they are used, you probably want to look at the source code for your test's Transport class.

* HTTP: Use <a href="https://github.com/twitter/iago/blob/master/src/main/scala/com/twitter/parrot/server/FinagleTransport.scala">FinagleTransport</a>
* Thrift: Use <a href="https://github.com/twitter/iago/blob/master/src/main/scala/com/twitter/parrot/server/ThriftTransport.scala">ThriftTransport</a>
* Memcached: Use <a href="https://github.com/twitter/iago/blob/master/src/main/scala/com/twitter/parrot/server/MemcacheTransport.scala">MemcacheTransport</a>
* Kestrel: Use <a href="https://github.com/twitter/iago/blob/master/src/main/scala/com/twitter/parrot/server/KestrelTransport.scala">KestrelTransport</a>
* UDP: Use <a href="https://github.com/twitter/iago/blob/master/src/main/scala/com/twitter/parrot/server/ParrotUdpTransport.scala">ParrotUdpTransport</a>

Your service is typically an HTTP or Thrift service written in either Scala or Java.

[Top](#Top)

<a name="Transaction Requirements"></a>

### Transaction Requirements

For replay, Iago recommends you scrub your logs to only include requests which meet the following requirements:

* **Idempotent**, meaning that re-execution of a transaction any number of times yields the same result as the initial execution.
* **Commutative**, meaning that transaction order is not important. Although transactions are initiated in replay order, Iago's internal behavior may change the actual execution order to guarantee the transaction rate. Also, transactions that implement `Future` responses are executed asynchronously. You can achieve ordering, if required, by using Iago as a library and initiating new requests in response to previous ones. Examples of this are available.

[Top](#Top)

<a name="Sources of Transactions"></a>

### Sources of Transactions

Transactions typically come from logs, such as the following:

* Web server logs capture HTTP transactions.
* Proxy server logs can capture transactions coming through a server. You can place a proxy server in your stack to capture either HTTP or Thrift transactions.
* Network sniffers can capture transactions as they come across a physical wire. You can program the sniffer to create a log of transactions you identify for capture.

In some cases, transactions do not exist. For example, transactions for your service may not yet exist because they are part of a new service, or you are obligated not to use transactions that contain sensitive information. In such cases, you can provide _synthetic_ transactions, which are transactions that you create to model the operating environment for your service. When you create synthetic transactions, you must statistically distribute your transactions to match the distribution you expect when your service goes live.

[Top](#Top)

<a name="Iago Architecture Overview"></a>

## Iago Architecture Overview

Iago consists of _feeders_ and _servers_. A _feeder_ reads your transaction source. A _server_ formats and delivers requests to the service you want to test. The feeder contains a `Poller` object, which is responsible for guaranteeing _cachedSeconds_ worth of transactions in the pipeline to the Iago servers.

Metrics are available in logs and in  graphs as described in [Metrics](#metrics).

The Iago servers generate requests to your service. Together, all Iago servers generate the specified number of requests per minute. A Iago server's `RecordProcessor` object executes your service and maps the transaction to the format required by your service.

The feeder polls its servers to see how much data they need to maintain _cachedSeconds_ worth of data. That is how we can have many feeders that need not coordinate with each other.

Ensuring that we go through every last message is important when we are writing traffic summaries in the record processor, especially when the data set is small. The parrot feeder shuts down due to running out of time, running out of data, or both. When the feeder runs out of data we

- make sure that all the data in parrot feeder's internal queues are sent to the parrot server
- make sure all the data held in the parrot servers cache is sent
- wait until we get a response for all pending messages or until the reads time out

When the parrot feeder runs out of time (the duration configuration) the data in the feeder's internal queues are ignored, otherwise the same process as above occurs.


[Top](#Top)

<a name="Implementing Your Test"></a>

## Implementing Your Test

The following sections show examples of implementing your test in both Scala and Java. See <a href="#Code Annotations for the Examples">Code Annotations for the Examples</a> for information about either example.

[Top](#Top)

<a name="Scala Example"></a>

### Scala Example

<p>To implement a load test in Scala, you must extend the Iago server's <code>RecordProcessor</code> class to specify how to map transactions into the requests that the Iago server delivers to your service. The following example shows a <code>RecordProcessor</code> subclass that implements a load test on an <code>EchoService</code> HTTP service:</p>

```scala
package com.twitter.example

import org.apache.thrift.protocol.TBinaryProtocol

import com.twitter.parrot.processor.RecordProcessor                                     // 1
import com.twitter.parrot.thrift.ParrotJob                                              // 2
import com.twitter.parrot.server.{ParrotRequest,ParrotService}                          // 3
import com.twitter.logging.Logger
import org.jboss.netty.handler.codec.http.HttpResponse

import thrift.EchoService

class EchoLoadTest(parrotService: ParrotService[ParrotRequest, HttpResponse]) extends RecordProcessor {
  val client = new EchoService.ServiceToClient(service, new TBinaryProtocol.Factory())  // 4
  val log = Logger.get(getClass)

  def processLines(job: ParrotJob, lines: Seq[String]) {                                // 5
    lines map { line =>
      client.echo(line) respond { rep =>
        if (rep == "hello") {
          client.echo("IT'S TALKING TO US")                                             // 6
        }
        log.info("response: " + rep)                                                    // 7
      }
    }
  }
}
```

[Top](#Top)

<a name="Scala Thrift Example"></a>

### Scala Thrift Example

<p>To implement a Thrift load test in Scala, you must extend the Iago server's <code>Thrift RecordProcessor</code> class to specify how to map transactions into the requests that the Iago server delivers to your service. The following example shows a <code>ThriftRecordProcessor</code> subclass that implements a load test on an <code>EchoService</code> Thrift service:</p>

```scala
package com.twitter.example

import org.apache.thrift.protocol.TBinaryProtocol

import com.twitter.parrot.processor.ThriftRecordProcessor                               // 1
import com.twitter.parrot.thrift.ParrotJob                                              // 2
import com.twitter.parrot.server.{ParrotRequest,ParrotService}                          // 3
import com.twitter.logging.Logger

import thrift.EchoService

class EchoLoadTest(parrotService: ParrotService[ParrotRequest, Array[Byte]]) extends ThriftRecordProcessor(parrotService) {
  val client = new EchoService.ServiceToClient(service, new TBinaryProtocol.Factory())  // 4
  val log = Logger.get(getClass)

  def processLines(job: ParrotJob, lines: Seq[String]) {                                // 5
    lines map { line =>
      client.echo(line) respond { rep =>
        if (rep == "hello") {
          client.echo("IT'S TALKING TO US")                                             // 6
        }
        log.info("response: " + rep)                                                    // 7
      }
    }
  }
}
```
		
[Top](#Top)


<a name="Java Example"></a>

### Java Example

<p>To implement a load test in Java, you must extend the Iago server's <code>LoadTest</code> class to specify how to map transactions into the requests that the Iago server delivers to your service. The <code>LoadTest</code> class provides Java-friendly type mappings for the underlying Scala internals. The following example shows a <code>LoadTest</code> subclass that implements a load test on an <code>EchoService</code> HTTP service:    </p>

```java
package com.twitter.jexample;

import com.twitter.example.thrift.EchoService;
import com.twitter.parrot.processor.LoadTest;                                           // 1
import com.twitter.parrot.thrift.ParrotJob;                                             // 2
import com.twitter.parrot.server.ParrotRequest;                                         // 3

import com.twitter.parrot.server.ParrotService;                                         // 3
import com.twitter.util.Future;
import com.twitter.util.FutureEventListener;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.jboss.netty.handler.codec.http.HttpResponse

import java.util.List;

public class EchoLoadTest extends LoadTest {
  EchoService.ServiceToClient client = null;

  public EchoLoadTest(ParrotService<ParrotRequest, HttpResponse> parrotService) {
    super(parrotService);
    client = new EchoService.ServiceToClient(service(), new TBinaryProtocol.Factory()); // 4
  }

  public void processLines(ParrotJob job, List<String> lines) {                         // 5
    for(String line: lines) {
      Future<String> future = client.echo(line);
      future.addEventListener(new FutureEventListener<String>() {
        public void onSuccess(String msg) {
          System.out.println("response: " + msg);
        }

      public void onFailure(Throwable cause) {
        System.out.println("Error: " + cause);
      }
     });
    }
  }
}
```
		
[Top](#Top)

<a name="Java Example"></a>

### Java Thrift Example

<p>To implement a Thrift load test in Java, you must extend the Iago server's <code>ThriftLoadTest</code> class to specify how to map transactions into the requests that the Iago server delivers to your service. The <code>ThriftLoadTest</code> class provides Java-friendly type mappings for the underlying Scala internals. The following example shows a <code>ThriftLoadTest</code> subclass that implements a load test on an <code>EchoService</code> Thrift service:    </p>

```java
package com.twitter.jexample;

import com.twitter.example.thrift.EchoService;
import com.twitter.parrot.processor.ThriftLoadTest;                                     // 1
import com.twitter.parrot.thrift.ParrotJob;                                             // 2
import com.twitter.parrot.server.ParrotRequest;                                         // 3
import com.twitter.parrot.server.ParrotService;                                         // 3
import com.twitter.util.Future;
import com.twitter.util.FutureEventListener;
import org.apache.thrift.protocol.TBinaryProtocol;

import java.util.List;

public class EchoLoadTest extends ThriftLoadTest {
  EchoService.ServiceToClient client = null;

  public EchoLoadTest(ParrotService<ParrotRequest, byte[]> parrotService) {
    super(parrotService);
    client = new EchoService.ServiceToClient(service(), new TBinaryProtocol.Factory()); // 4
  }

  public void processLines(ParrotJob job, List<String> lines) {                         // 5
    for(String line: lines) {
      Future<String> future = client.echo(line);
      future.addEventListener(new FutureEventListener<String>() {
        public void onSuccess(String msg) {
          System.out.println("response: " + msg);
        }

      public void onFailure(Throwable cause) {
        System.out.println("Error: " + cause);
      }
     });
    }
  }
}
```
		
[Top](#Top)

<a name="Code Annotations for the Examples"></a>

### Code Annotations for the Examples

You define your Iago subclass to execute your service and map transactions to requests for your service:

1. Import `com.twitter.parrot.processor.RecordProcessor` (Scala) or `LoadTest` (Java), whose instance will be executed by a Iago server.
2. Import `com.twitter.parrot.thrift.ParrotJob`, which contains the Iago server class.
3. Import `com.twitter.parrot.server.ParrotService` and `com.twitter.parrot.server.ParrotRequest`
4. Create an instance of your service to be placed under test. Your service is a client of the Iago service.
5. Define a `processLines` method to format the request and and execute your service.
6. Optionally, you can initiate a new request based on the response to a previous one.
7. Optionally, do something with the response. In this example, the response is logged.

[Top](#Top)

<a name="Configuring Your Test"></a>

## Configuring Your Test

To configure your test, create a `launcher.scala` file that that creates a `ParrotLauncherConfig` instance with the configuration parameters you want to set.

There are several parameters to set. A good one to <a href="#Supported Services">figure out early is <code>transport</code></a>; that will in turn help you to find out what, e.g., <code>responseType</code> you need.

The following example shows parameters for testing a Thrift service:

```scala
import com.twitter.parrot.config.ParrotLauncherConfig

new ParrotLauncherConfig {
  distDir = "."
  jobName = "load_echo"
  port = 8080
  victims = "localhost"
  log = "logs/yesterday.log"
  requestRate = 1
  numInstances = 1
  duration = 5
  timeUnit = "MINUTES" // affects duration; does not affect requestRate

  imports = "import com.twitter.example.EchoLoadTest"
  responseType = "Array[Byte]"
  transport = "ThriftTransportFactory(this)"
  loadTest = "new EchoLoadTest(service.get)"
}
```

**Note:** For a sample configuration file, see `config/launcher.scala` within the Iago distribution</a>.

You can specify any of the following parameters:

<table border="1" cellpadding="1">
<thead>
<tr>
<th>Parameter</th>
<th>Description</th>
<th>Required or<br/>Default Value</th>
</tr>
</thead>

<tr>
    <td><code>createDistribution</code></td>
    <td><p>You can use this field to create your own distribution rate, instead of having a constant flow. You will need to create a subclass of RequestDistribution and import it.</p>
    <p><b>Example: </b><pre>createDistribution = """createDistribution = {
  rate => new MyDistribution(rate)
}"""</pre></p></td>
    <td><i>""</i></td>
</tr>

<tr>
<td><code>customLogSource</code></td> <td><p>A string with Scala code that will be put into the
    Feeder config. You can use this to get Iago to read in compressed files. Iago can read LZO
    compressed files using its built-in LzoFileLogSource.</p>
    <p><b>Example:</b><pre>customLogSource = """
  if(inputLog.endsWith(".lzo")) {
    logSource = Some(new com.twitter.parrot.feeder.LzoFileLogSource(inputLog))
  }"""
    </pre></p></td> <td><i>""</i></td>
    </tr>

<tr>
    <td><code>distDir</code></td>
    <td><p>The subdirectory of your project you're running from, if any.</p>
    <p><b>Example: </b><code>distDir = "target"</code></p></td>
    <td><i>"."</i></td>
</tr>

<tr>
    <td><code>doConfirm</code></td>
    <td><p>If set to false, you will not be asked to confirm the run.</p>
    <p><b>Example: </b><code>doConfirm = false</code></p></td>
    <td><i>true</i></td>
</tr>

<tr>
    <td><code>duration</code></td>
    <td><p>An integer value that specifies the time to run the test in <code>timeUnit</code> units.</p>
    <p><b>Example: </b><code>duration = 5</code></p></td>
    <td><code>&nbsp;</code></td>
</tr>

<tr>
    <td><code>feederXmx</code></td>
    <td><p>Defines feeder heap size. Suggested not to be higher than 4 GB (will cause issues scheduling)</p>
    <p><b>Example: </b><code>feederXmx = 2048</code></p></td>
    <td><i>1744</i></td>
</tr>

<tr>
    <td><code>header</code></td>
    <td><p>A string value that specifies the HTTP Host header.</p>
    <p><b>Example: </b><code>header = "api.yourdomain.com"</code></p></td>
    <td><code>""</code></td>
</tr>

<tr>
    <td><code>hostConnectionCoresize</code></td>
    <td><p>Number of connections per host that will be kept open, once established, until they hit max idle time or max lifetime</p>
    <p><b>Example: </b><code>hostConnectionCoresize = 1</code></p></td>
    <td><i>1</i></td>
</tr>

<tr>
    <td><code>hostConnectionIdleTimeInMs</code></td>
    <td><p>For any connection > coreSize, maximum amount of time, in milliseconds, between requests we allow before shutting down the connection</p>
    <p><b>Example: </b><code>hostConnectionIdleTimeInMs = 50000</code></p></td>
    <td><i>60000</i></td>
</tr>

<tr>
    <td><code>hostConnectionLimit</code></td>
    <td><p>Limit on the number of connections per host</p>
    <p><b>Example: </b><code>hostConnectionLimit = 4</code></p></td>
    <td><i>Integer.MAX_VALUE</i></td>
</tr>
<tr>
    <td><code>hostConnectionMaxIdleTimeInMs</code></td>
    <td><p>The maximum time in milliseconds that any connection (including within core size) can stay idle before shutdown</p>
    <p><b>Example: </b><code>hostConnectionMaxIdleTimeInMs = 500000</code></p></td>
    <td><i>300000</i></td>
</tr>
<tr>
    <td><code>hostConnectionMaxLifeTimeInMs</code></td>
    <td><p>The maximum time in milliseconds that a connection will be kept open</p>
    <p><b>Example: </b><code>hostConnectionMaxLifeTimeInMs = 10000</code></p></td>
    <td><i>Integer.MAX_VALUE</i></td>
</tr>

<tr>
  <td><code>jobName</code></td>
  <td><p>A string value that specifies the the name of your test. This is used for two things:
      <ol>
	<li>if the parrot feeder is configured to find its servers using zookeeper, and/or </li>
	<li>when using mesos it is part of the job names generated. A job name of "foo" results in mesos job sharding groups "parrot_server_foo" and "parrot_feeder_foo".</li>
      </ol>
    </p>
    <p><b>Example: </b><code>jobName = "testing_tasty_new_feature"</code></p></td>
    <td><b>Required</b></td>
</tr>

<tr>
    <td><code>localMode</code></td>
    <td><p>Should Iago attempt to run locally or to use the cluster via mesos?</p>
    <p><b>Example: </b><code>localMode = true</code></p></td>
    <td><i>false</i></td>
</tr>

<tr>
    <td><code>log</code></td>
    <td><p>A string value that specifies the complete path to the log you want Iago to replay. If localMode=true then the log should be on your local file system. The log should have at least 1000 items or you should change the <code>reuseFile</code> parameter.</p>
    <p><b>Example: </b><code>log = "logs/yesterday.log"</code></p>
    <p><p>If localMode=false (the default), then the parrot launcher will copy your log file when attempts to make a package for mesos. You can avoid this, and should, by storing your log file in HDFS.<p><b>Example: </b><code>log = "hdfs://hadoop-example.com/yesterday.log"</code></p></td>
    <td><b>Required</b></td>
    <td><b>Required</b></td>
</tr>

<tr>
    <td><code>loggers</code></td>
    <td><p>A List of LoggerFactories; allows you to define the type and level of logging you want</p>
    <p><b>Example:</b></p>
<pre>import com.twitter.logging.LoggerFactory
import com.twitter.logging.config._

new ParrotLauncherConfig {
  ...
  loggers = new LoggerFactory(
    level = Level.DEBUG,
    handlers = new ConsoleHandlerConfig()
  )
} </pre></td>
    <td><i>Nil</i></td>
</tr>

<tr>
    <td><code>maxRequests</code></td>
    <td><p>An integer value that specifies the total number of requests to submit to your service.</p>
    <p><b>Example: </b><code>maxRequests = 10000</code></p></td>
    <td><code>1000</code></td>
</tr>

<tr>
    <td><code>requestRate</code></td>
    <td><p>An integer value that specifies the number of requests per second to submit to your service.</p>
    <p><b>Example: </b><code>requestRate = 10</code></p>
    <p>Note: if using multiple server instances, requestRate is per-instance, not aggregate.</p></td>
    <td><code>1</code></td>
</tr>

<tr>
    <td><code>reuseFile</code></td>
    <td><p>A boolean value that specifies whether or not to stop the test when the input log has been read through. Setting this value to true will result in Iago starting back at the beginning of the log when it exhausts the contents. If this is true, your log file should at least be 1,000 lines or more.</p>
    <p><b>Example: </b><code>reuseFile = false</code></p></td>
    <td><code>true</code></td>
</tr>

<tr>
    <td><code>scheme</code></td>
    <td><p>A string value that specifies the scheme portion of a URI.</p>
    <p><b>Example: </b><code>scheme = "http"</code></p></td>
    <td><code>http</code></td>
</tr>

<tr>
    <td><code>serverXmx</code></td>
    <td><p>Defines server heap size. Suggested not to be higher than 8 GB (will cause issues scheduling)</p>
    <p><b>Example: </b><code>serverXmx = 5000</code></p></td>
    <td><i>4000</i></td>
</tr>

<tr>
  <td><code>requestTimeoutInMs</code></td>
  <td>
    <p>(From the Finagle Documentation) The request timeout is the time given to a *single* request (if there are retries, they each get a fresh request timeout). The timeout is applied only after a connection has been acquired. That is: it is applied to the interval between the dispatch of the request and the receipt of the response.</p>
    <p>Note that parrot servers will not shut down until every response from every victim has come in. If you've modified your record processor to write test summaries this can be an issue.</p>
    <p><b>Example: </b><code>requestTimeoutInMs = 3000 // if the victim doesn't respond in three seconds, stop waiting</code></p>
  </td>
  <td><code>30000 // 30 seconds</code></td>
</tr>

<tr>
    <td><code>reuseConnections</code></td>
    <td><p>A boolean value that specifies whether connections to your service's hosts can be reused. A value of <code>true</code> enables reuse. Setting this to false greatly increases your use of ephemeral ports and can result in port exhaustion, causing you to achieve a lower rate than requested</p>
      <p>This is only implemented for FinagleTransport.</p>
    <p><b>Example: </b><code>reuseConnections = false</code></p></td>
    <td><code>true</code></td>
</tr>

<tr>
    <td><code>thriftClientId</code></td>
    <td><p>If you are making Thrift requests, your clientId</p>
    <p><b>Example: </b><code>thriftClientId = "projectname.staging"</code></p></td>
    <td><i>""</i></td>
</tr>

<tr>
    <td><code>timeUnit</code></td>
    <td><p>A string value that specifies time unit of the <code>duration</code>. It contains one of the following values:
        <ul>
            <li> "MINUTES"
            <li> "HOURS"
            <li> "DAYS"
        </ul></p>
    <p><b>Example: </b><code>timeUnit = "MINUTES"</code></p></td>
    <td><code>&nbsp;</code></td>
</tr>


<tr>
    <td><code>traceLevel</code></td>
    <td><p>A <code>com.twitter.logging.Level</code> subclass. Controls the level of "debug logging" for servers and feeders.</p>
    <p><b>Example:</b>
<pre>traceLevel = com.twitter.logging.Level.TRACE</pre>
</p></td>
    <td><code>Level.INFO</code></td>
</tr>

<tr>
    <td><code>verboseCmd</code></td>
    <td><p>A boolean value that specifies the level of feedback from Iago. A value of <code>true</code> specifies maximum feedback.</p>
    <p><b>Example: </b><code>verboseCmd = true</code></p></td>
    <td><code>false</code></td>
</tr>
</tbody>
</table>

<a name="Specifying_Victims"></a>

#### [Specifying Victims]

The point of Iago is to load-test a service. Iago calls these "victims".


Victims may be a

1. single host:port pair
2. list of host:port pairs
3. a zookeeper serverset

Note that ParrotUdpTransport can only handle a single host:port pair. The other transports that come with Iago, being Finagle based, do not have this limitation.

<table border="1" cellpadding="6">
<thead>
<tr>
<th>Parameter</th>
<th>Description</th>
<th>Required or<br/>Default Value</th>
</tr>
</thead>

<tr>
  <td><code>victims</code></td>
  <td><p>A list of host:port pairs:</p>
  <code>victims="example.com:80 example2.com:80"</code>
  <p/><p>A zookeeper server set:</p>
  <code>victims="/some/zookeeper/path"</code>
  </td>
  <td><b>Required</b></td>
</tr>

<tr>
    <td><code>port</code></td>
    <td><p>An integer value that specifies the port on which to deliver requests to the <code>victims</code>.</p>
    <p>The port is used for two things: to provide a port if none were specified in victims, and to provide a port for the host header using a FinagleTransport.</p>
    <p><b>Example: </b><code>port = 9000</code></p></td>
    <td><b>Required</b></td>
</tr>

<tr>
<td><code>victimClusterType</code></td>
  <td>
  <p>When victimClusterType is "static", we set victims and port. victims can be a single host name, a host:port pair, or a list of host:port pairs separated with commas or spaces.</p>
  <p>When victimClusterType is "sdzk" (which stands for "service discovery zookeeper") the victim is considered to be a server set, referenced with victims, victimZk, and victimZkPort.</p></td>
<td>Default: <code>"static"</code></td>
</tr>
<tr>
<td><code>victimZk</code></td>
<td><p>the host name of the zookeeper where your serverset is registered</p></td>
<td><p>Default is <code>"sdzookeeper.local.twitter.com"</code></p></td>
</tr>

<tr>
  <td><code>victimZkPort</code></td>
  <td><p>The port of the zookeeper where your serverset is registered</p></td>
  <td><p>Default: <code>2181</code></p></td>
</tr>
</table>

<a name="extension_point_parameters"></a>

#### [Extension Point Parameters]

<p><strong>Alternative Use:</strong> You can specify the following <em>extension point</em> parameters to configure projects in which Iago is used as both a feeder and server. The Iago feeder provides the log lines to your project, which uses these log lines to form requests that the Iago server then handles:</p>

<table border="1" cellpadding="6">
<thead>
<tr>
<th>Parameter</th>
<th>Description</th>
<th>Required or<br/>Default Value</th>
</tr>
</thead>
<tr>
    <td><code>imports</code></td>
    <td><p>Imports from this project to Iago</p>
    <p><b>Example: </b>If <code>ProjectX</code> includes Iago as a dependency, you would specify: <br/>
    <code>import org.jboss.netty.handler.codec.http.HttpResponse <br/>
    import com.twitter.<i>projectX</i>.util.ProcessorClass</code></p></td>
    <td><code>import org.jboss.netty.handler.codec.http.HttpResponse<br/>
    import com.twitter.parrot.util.LoadTestStub</code></td>
</tr>
<tr>
    <td><code>requestType</code></td>
    <td><p>The request type of requests from Iago.</p>
    <p><b>Examples:</b>
        <ul>
            <li> <code>ParrotRequest</code> for most services (including HTTP and Thrift)
        </ul> </p>
    </td>
    <td><code>ParrotRequest</code></td>
</tr>
<tr>
    <td><code>responseType</code></td>
    <td><p>The response type of responses from Iago.</p>
    <p><b>Examples:</b>
        <ul>
            <li> <code>HttpResponse</code> for an HTTP service
            <li> <code>Array[Byte]</code> for a Thrift service
        </ul> </p>
    </td>
    <td><code>HttpResponse</code></td>
</tr>
<tr>
  <td><code>transport</code></td>
  <td>
    <p>The kind of transport to the server, which matches the <code>responseType</code> you want.</p>
    <p><b>Example:</b><code>transport = "ThriftTransportFactory(this)"</code></p>
    <p>The Thrift Transport will send your request and give back <code>Future[Array[Byte]]</code>.</p>
  </td>
  <td><code>FinagleTransport</code></td>
</tr>
<tr>
    <td><code>loadTest</code></td>
    <td><p>Your processor for the Iago feeder's lines, which converts the lines into requests and sends them to the Iago server.</p>
    <p><b>Example: </b><code>new LoadTestStub(service.get)</code></p></td>
    <td><code>new LoadTestStub(service.get)</code></td>
</tr>
</tbody>
</table>

[Top](#Top)

<a name="sending_large_messages"></a>

#### [Sending Large Messages]

By default, the parrot feeder sends a thousand messages at a time to each connected parrot server until the parrot server has twenty seconds worth of data. This is a good strategy when messages are small (less than a kilobyte). When messages are large, the parrot server will run out of memory. Consider an average message size of 100k, then the feeder will be maintaining an output queue for each connected parrot server of 100 million bytes. For the parrot server, consider a request rate of 2000, then 2000 * 20 * 100k = 4 gigabytes (at least). The following parameters help with large messages:

<table border="1" cellpadding="6">
<thead>
<tr>
<th>Parameter</th>
<th>Description</th>
<th>Required or<br/>Default Value</th>
</tr>
</thead>
<tr>
  <td><code>batchSize</code></td>
  <td>
    <p>how many messages the parrot feeder sends at one time to the
    parrot server. For large messages, setting this to 1 is
    recommended.</p></td>
  <td>Default: <code>1000</code></td>
</tr>
<tr>
<td><code>cachedSeconds</code></td>
<td><p>How many seconds worth of data the parrot server will attempt to cache. Setting this to 1 for large messages is recommended. The consequence is that, if the parrot feeder garbage-collects, there will be a corresponding pause in traffic to your service unless cachedSeconds is set to a value larger than a typical feeder gc. This author has never observed a feeder gc exceeding a fraction of a second.</p></td>
<td><p>Default is <code>20</code></p></td>
</tr>
</table>

[Top](#Top)

<a name="weighted_requests"></a>

#### [Weighted Requests]

Some applications must make bulk requests to their service. In other words, a single meta-request in the input log may result in several requests being satisfied by the victim. A weight field to ParrotRequest was added so that the RecordProcessor can set and use that weight to control the send rate in the RequestConsumer. For example, a request for 17 messages would be given a weight of 17 which would cause the RequestConsumer to sample the request distribution 17 times yielding a consistent distribution of load on the victim.

[Top](#Top)

<a name="metrics"></a>

## [Metrics]

Iago uses [Ostrich](https://github.com/twitter/ostrich) to record its metrics. Iago is configured so that a simple graph server is available as long as the parrot server is running. If you are using localMode=true, then the default place for this is

&nbsp;&nbsp;[http://localhost:9994/graph/](http://localhost:9994/graph/)

One metric of particular interest is
 
&nbsp;&nbsp;[http://localhost:9994/graph/?g=metric:client/request_latency_ms](http://localhost:9994/graph/?g=metric:client/request_latency_ms)

Request latency is the time it takes to queue the request for sending until the response is received. See the [Finagle User Guide](http://twitter.github.io/finagle/guide/Metrics.html) for more about the individual metrics.


Other metrics of interest:

<table border="1" cellpadding="6">
<thead>
<tr>
<th>Statistic</th>
<th>Description</th>
</tr>
</thead>
<tr>
    <td><code>connection_duration</code></td>
    <td>duration of a connection from established to closed?</td>
</tr>
<tr>
    <td><code>connection_received_bytes</code></td>
    <td>bytes received per connection</td>
</tr>
<tr>
    <td><code>connection_requests</code></td>
    <td>Number of connection requests that your client did, ie. you can have a pool of 1 connection and the connection can be closed 3 times, so the "connection_requests" would be 4 (even if connections = 1)</td>
</tr>
<tr>
    <td><code>connection_sent_bytes</code></td>
    <td>bytes send per connection</td>
</tr>
<tr>
    <td><code>connections</code></td>
    <td>is the current number of connections between client and server</td>
</tr>
<tr>
    <td><code>handletime_us</code></td>
    <td>time to process the response from the server (ie. execute all the chained map/flatMap)</td>
</tr>
<tr>
    <td><code>pending</code></td>
    <td>Number of pending requests (ie. requests without responses)</td>
</tr>
<tr>
    <td><code>request_concurrency</code></td>
    <td>is the current number of connections being processed by finagle</td>
</tr>
<tr>
    <td><code>request_latency_ms</code></td>
    <td>the time of everything between request/response.</td>
</tr>
<tr>
    <td><code>request_queue_size</code></td>
    <td>Number of requests waiting to be handled by the server</td>
</tr>
<tr>
</table>


### [Raggiana]

Raggiana is a simple standalone Finagle stats viewer.

You can use Raggiana to view the stats log, <a href="#artifacts">parrot-server-stats.log</a>, generated by Iago.

You can clone it from

https://github.com/twitter/raggiana

or, just use it directly at

http://twitter.github.io/raggiana

[Top](#Top)

<a name="tracing"></a>

## [Tracing]

Parrot works with [Zipkin](http://twitter.github.io/zipkin/), a distributed tracing system.

[Top](#Top)

<a name="artifacts"></a>

## [What Files Are Created?]

The Iago launcher creates the following files

	config/target/parrot-feeder.scala
	config/target/parrot-server.scala
	scripts/common.sh
	scripts/parrot-feeder.sh
	scripts/parrot-server.sh

The Iago feeder creates

	parrot-feeder.log
	gc-feeder.log

The Iago server creates

	parrot-server.log
	parrot-server-stats.log
	gc-server.log 

The logs are rotated by size. Each individual log can be up to 100 megabytes before being rotated. There are 6 rotations maintained.

The stats log, `parrot-server-stats.log`, is a minute-by-minute dump of all the statistics (or <a
href="#metrics">Metrics</a>) maintained by the Iago server. Each entry is for the time period since
the previous one. That is, all entries in `parrot-server-stats.log` need to be accumulated to match
the final values reported by [http://localhost:9994/stats.txt](http://localhost:9994/stats.txt).

[Top](#Top)

## Using Iago as a Library

While Iago provides everything you need to target your API with a large distributed loadtest with just a small log processor,
it also exposes a library of classes for log processing, traffic replay, & load generation. These can be used in your Iago configuration or incorporated in your application as a library.

parrot/server:

* ParrotRequest: Parrot's internal representation of a request
* ParrotTransport (FinagleTransport, KestrelTransport, MemcacheTransport, ParrotUdpTransport, ThriftTransport): Interchangeable transport layer for requests to be sent. Parrot contains transport implementations for the following protocols: HTTP (FinagleTransport), Kestrel, Memcache, raw UDP and Thrift.
* RequestConsumer: Queues ParrotRequests and sends them out on a ParrotTransport at a rate determined by RequestDistribution
* RequestQueue: A wrapper/control layer for RequestConsumer
* ParrotService (ParrotThriftService): Enqueues ParrotRequests to a RequestQueue. ParrotThriftService implements finagle's Service interface for use with finagle thrift clients.

parrot/util:

* RequestDistribution: A function specifying the time to arrival of the next request, used to control the request rate. Instances include
	* UniformDistribution: Sends requests at a uniform rate
	* PoissonProcess: Sends requests at approximatly constant rate randomly varying using a poisson process. This is the default.
	* SinusoidalPoissonProcess: Like PoissonProcess but varying the rate sinusoidally.
	* SlowStartPoissonProcess: Same as PoissonProcess but starting with a gradual ramp from initial rate to final rate. It will then hold steady at the final rate until time runs out.
	* InfiniteRampPoissonProcess: a two staged ramped distribution. Ideal for services that need a warm-up period before ramping up. The rate continues to increase until time runs out.

You may also find the LogSource and RequestProcessor interfaces discussed earlier useful.

Examples:
<pre>
// Make 1000 HTTP requests at a roughly constant rate of 10/sec

// construct the transport and queue
val client =
  ClientBuilder()
    .codec(http())
    .hosts("twitter.com:80")
    .build()
val transport = new FinagleTransport(FinagleService(client))
val consumer = new RequestConsumer(() => new PoissionProcess(10)
// add 1000 requests to the queue
for (i <- (1 to 1000)) {
  consumer.offer(new ParrotRequest(uri= Uri("/jack/status/20", Nil))
}
// start sending
transport.start()
consumer.start()
// wait for the comsumer to exhaust the queue
while(consumer.size > 0) {
  Thread.sleep(100)
}
// shutdown
consumer.shutdown()
transport.close()
</pre>

<pre>
// Call a thrift service with a sinusoidally varying rate

// Configure cluster for the service using zookeeper
val zk = "zookeeper.example.com"
val zkPort = 2181
val path = "my/env/role/service"
val zookeeperClient = new ZooKeeperClient(Amount.of(1, Time.SECONDS),
  Seq(InetSocketAddress.createUnresolved(zk, zkPort)).asJava)
val serverSet = new ServerSetImpl(zookeeperClient, path)
val cluster = new ZookeeperServerSetCluster(serverSet)

// create transport and queue
val client =
  ClientBuilder()
    .codec(ThriftClientFramedCodec)
    .cluster(cluster)
    .build()
val transport = new ThriftTransport(client)
val createDistribution = () => new SinusoidalPoisionProccess(10, 20, 60.seconds)
val queue = new RequestQueue(new RequestConsumer(createDistribution, transport), transport)
// create the service and processor
val service = transport.createService(queue)
val processor = new EchoLoadTest(service)
// start sending
transport.start()
consumer.start()
// Fill the queue from a logfile
val source = new LogSourceImpl("some_file.txt")
while (source.hasNext) {
  processor.processLines(Seq(source.next))
}
// wait for the comsumer to exhaust the queue
while(consumer.size > 0) {
  Thread.sleep(100)
}
// shutdown
consumer.shutdown()
transport.close()
</pre>

[Top](#Top)

<a name="ChangeLog"></a>

## [ChangeLog]

2013-06-25  release 0.6.7

* graceful shutdown for small log sources
* dropped vestigial parser config
* weighted parrot requests
* supporting large requests (BlobStore): new configurations cachedSeconds & mesosRamInMb
* launcher changes: configurable proxy, create config directory if needed, and handle errors better (don't hang)
* serversets as victims
* make local logs work with non-local distribution directories
* kestrel transport transactional get support
* check generated config files *before* launch
* LzoFileLogSource for iago
* Thrift over TLS
* traceLevel config

[Top](#Top)

<a name="Contributing"></a>

## [Contributing to Iago]

Iago is open source, hosted on Github <a href="http://github.com/twitter/iago">here</a>.
If you have a contribution to make, please fork the repo and submit a pull request.

