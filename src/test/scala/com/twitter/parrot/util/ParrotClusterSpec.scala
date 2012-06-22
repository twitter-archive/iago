/*
Copyright 2012 Twitter, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.twitter.parrot.util

import collection.JavaConversions._
import com.google.common.collect.ImmutableSet
import com.twitter.common.application.ShutdownRegistry
import com.twitter.common.zookeeper.testing.ZooKeeperTestServer
import com.twitter.thrift.ServiceInstance
import java.util.concurrent.LinkedBlockingQueue
import org.specs.SpecificationWithJUnit

class ParrotClusterSpec extends SpecificationWithJUnit {
  val shutdownRegistry = new ShutdownRegistry.ShutdownRegistryImpl
  val zkTestServer = new ZooKeeperTestServer(0, shutdownRegistry)
  val zkNode = "/twitter/service/parrot/testNode"

  zkTestServer.startNetwork();

  /**
   * The Zookeeper libraries provide a way to create a local client directly using the in-memory
   * server. We want to use this method for testing, but it doesn't correspond at all to how the
   * client is created in production. Therefore the odd signature here.
   */
  def createDiscovery = { new Discovery(None, -1, zkNode, Some(zkTestServer.createClient)) }

  "Discovery" should {

    doAfter {
      shutdownRegistry.execute()
    }

    "let us join a ZK cluster" >> {
      val disco = createDiscovery.join(9999)
      disco.connected must_== true

      disco.shutdown()
    }

    /* This test is DEPRECATED -- calling get on the client reconnects us */
    "let us leave a ZK cluster" >> {
      val disco = createDiscovery.join(9999)
      disco.shutdown()
      disco.connected must_== true // should be false! calling connected reconnects us

      disco.shutdown() // yes, we just called it. oh well.
    }

    "let us monitor a ZK cluster we're joined to" >> {
      val cluster = new ParrotClusterImpl() {
        override def handleClusterEvent(set: ImmutableSet[ServiceInstance]) = ()
      }
      val disco = createDiscovery.join(9999).monitor(cluster)

      cluster.instances.size must_== 1

      disco.shutdown()
    }

    "let us monitor a ZK cluster we're a client of" >> {
      val cluster = new ParrotClusterImpl() {
        override def handleClusterEvent(set: ImmutableSet[ServiceInstance]) = ()
      }
      val server = createDiscovery.join(9999)
      val client = createDiscovery.monitor(cluster)

      cluster.instances.size must_== 1

      server.shutdown()
      client.shutdown()
    }

    "notice when a member enters" >> {
      val queue = new LinkedBlockingQueue[ImmutableSet[ServiceInstance]]
      val cluster = new ParrotClusterImpl {
        override def handleClusterEvent(set: ImmutableSet[ServiceInstance]) {
          queue.offer(set)
        }
      }
      val server1 = createDiscovery.join(9999)
      val client = createDiscovery.monitor(cluster)

      queue.take.size must_== 1

      val server2 = createDiscovery.join(9998)

      queue.take.size must_== 2

      server1.shutdown()
      server2.shutdown()
      client.shutdown()
    }

    "notice when a member leaves" >> {
      val queue = new LinkedBlockingQueue[ImmutableSet[ServiceInstance]]
      val cluster = new ParrotClusterImpl {
        override def handleClusterEvent(set: ImmutableSet[ServiceInstance]) {
          queue.offer(set)
        }
      }
      val server = createDiscovery.join(9999)
      val client = createDiscovery.monitor(cluster)

      queue.take.size must_== 1

      server.shutdown()

      queue.take.size must_== 0

      client.shutdown()
    }

    "see members that are joined before us" >> {
      val queue = new LinkedBlockingQueue[ImmutableSet[ServiceInstance]]
      val cluster = new ParrotClusterImpl {
        override def handleClusterEvent(set: ImmutableSet[ServiceInstance]) {
          queue.offer(set)
        }
      }
      val server1 = createDiscovery.join(9999)
      val server2 = createDiscovery.join(9998)
      val client = createDiscovery.monitor(cluster)

      queue.take.size must_== 2

      server1.shutdown()
      server2.shutdown()
      client.shutdown()
    }
  }
}
