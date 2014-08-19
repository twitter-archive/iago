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

import collection.JavaConverters._
import collection.mutable
import com.google.common.collect.ImmutableSet
import com.twitter.common.quantity.{ Time, Amount }
import com.twitter.common.net.pool.DynamicHostSet.HostChangeMonitor
import com.twitter.common.zookeeper.{ ServerSetImpl, ZooKeeperClient }
import com.twitter.common.zookeeper.ServerSet.EndpointStatus
import com.twitter.logging.Logger
import com.twitter.parrot.config.ParrotCommonConfig
import com.twitter.parrot.thrift.ParrotServerService
import com.twitter.thrift.{ Endpoint, ServiceInstance, Status }
import com.twitter.util.Duration
import java.net.{ InetAddress, InetSocketAddress }
import java.util.concurrent.{ CountDownLatch, TimeUnit }
import com.twitter.logging.Level

class Discovery(zkHostName: Option[String], zkPort: Int, zkNode: String, providedClient: Option[ZooKeeperClient] = None) {
  val log = Logger.get(getClass.getName)

  log.logLazy(Level.DEBUG, "Discovery: zkHostName = " + zkHostName + ", zkPort = " + zkPort + ", zkNode = " + zkNode);

  val zkCluster: Array[InetSocketAddress] = zkHostName match {
    case None => Array()
    case Some(host) => try {
      InetAddress.getAllByName(host).map(new InetSocketAddress(_, zkPort))
    } catch {
      case t: Throwable => {
        log.error("Error getting Zookeeper address.", t)
        Array()
      }
    }
  }

  private[this] lazy val zkClient = providedClient.getOrElse(
    new ZooKeeperClient(Amount.of(1000, Time.MILLISECONDS), zkCluster.toIterable.asJava))
  private[this] lazy val zkServerSet = new ServerSetImpl(zkClient, zkNode)
  private[this] var zkStatus: Option[EndpointStatus] = None

  def join(port: Int, shardId: Int = 0): Discovery = {
    if (!connected) {
      return this
    }
    zkStatus = Some(zkServerSet.join(
      InetSocketAddress.createUnresolved(InetAddress.getLocalHost.getHostName, port),
      Map[String, InetSocketAddress]() asJava,
      shardId))
    log.logLazy(Level.DEBUG, "zkStatus is " + zkStatus)
    this
  }

  def monitor(monitor: HostChangeMonitor[ServiceInstance]) = {
    if (connected) {
      zkServerSet.monitor(monitor)
    }
    this
  }

  def shutdown() {
    zkStatus map (_.leave())
    zkClient.close()
  }

  def pause() {
  }

  def resume() {
  }

  def connected: Boolean = {
    try {
      log.logLazy(Level.DEBUG, "providedClient.isEmpty && zkCluster.isEmpty = " +
        providedClient.isEmpty + " && " + zkCluster.isEmpty)
      // if a client was provided, we don't care about the zkCluster values.
      // Otherwise, we need to not call get on a lazy client, because ZKClient
      // complains if you give it an empty list of zk servers
      if (providedClient.isEmpty && zkCluster.isEmpty) {
        false
      } else {
        zkClient.get(Amount.of(10, Time.SECONDS))
        true
      }
    } catch {
      case t: Throwable => {
        log.error(t, "Error connecting to Zookeeper: %s", t.getClass.getName)
        false
      }
    }
  }
}

trait ParrotCluster {
  def start(port: Int)
  def runningParrots: Set[RemoteParrot]
  def pausedParrots: Set[RemoteParrot]
  def parrots: Set[RemoteParrot]
  def pause()
  def resume()
  def shutdown()
}

/**
 * This class does Zookeeper discovery and creates RemoteParrots to wire them up.
 */
class ParrotClusterImpl(config: Option[ParrotCommonConfig] = None)
  extends HostChangeMonitor[ServiceInstance]
  with ParrotCluster {
  val log = Logger.get(getClass.getName)
  private[this] val members = new CountDownLatch(1)
  private[this] lazy val discovery = new Discovery(getConfig.zkHostName, getConfig.zkPort, getConfig.zkNode)

  @volatile
  var instances = Set[ServiceInstance]()

  private[this] val _runningParrots = mutable.HashSet[RemoteParrot]()
  private[this] val _pausedParrots = mutable.HashSet[RemoteParrot]()

  def getConfig: ParrotCommonConfig = {
    config.getOrElse(throw new RuntimeException("Tried to fetch config in an unconfigured context"))
  }

  def connectParrots(): List[RemoteParrot] = {
    log.info("Connecting to Parrots")
    config.get.zkHostName match {
      case Some(_) => connectParrotsFromZookeeper(getConfig)
      case None    => connectParrotsFromConfig(getConfig)
    }
  }

  def runningParrots: Set[RemoteParrot] = _runningParrots.toSet
  def pausedParrots: Set[RemoteParrot] = _pausedParrots.toSet
  def parrots: Set[RemoteParrot] = runningParrots ++ pausedParrots

  /**
   * This isn't efficient for large clusters. We would want to use a different
   * data structure for this, since membership changes would be frequent
   * and scanning the large lists with an O(n2) algorithm would be poor.
   */
  private[this] def updateParrots(hostSet: ImmutableSet[ServiceInstance]) {
    hostSet.asScala.foreach { instance =>
      val endpoint = instance.getServiceEndpoint
      if (instance.getStatus == Status.STOPPING && !isPaused(endpoint)) {
        pauseParrot(endpoint)
      } else if (instance.getStatus == Status.ALIVE && isPaused(endpoint)) {
        resumeParrot(endpoint)
      } else {
        updateParrotMembership(endpoint)
      }
    }
  }

  private[this] def updateParrotMembership(endpoint: Endpoint) {
    _runningParrots.find(p => p.host == endpoint.host && p.port == endpoint.port) match {
      case Some(parrot) => {
        if (!isParrotConnected(parrot)) {
          log.info("Removing parrot %s:%d", parrot.host, parrot.port)
          _runningParrots -= parrot
        }
      }
      case None => {
        if (!isPaused(endpoint)) {
          addNewParrot(endpoint)
        }
      }
    }
  }

  private[this] def addNewParrot(endpoint: Endpoint) {
    connectParrot(endpoint.host, endpoint.port) match {
      case Some(parrot) => {
        _runningParrots += parrot
        log.debug("added a new parrot: %s:%d, paused=%d unpaused=%d",
          parrot.host, parrot.port, _pausedParrots.size, _runningParrots.size)
      }
      case None => ()
    }
  }

  private[this] def isPaused(endpoint: Endpoint) = {
    val parrot = _pausedParrots.find(p => p.host == endpoint.host && p.port == endpoint.port)
    parrot match {
      case None         => false
      case Some(paused) => true
    }
  }

  private[this] def resumeParrot(endpoint: Endpoint) {
    val parrot = _pausedParrots.find(p => p.host == endpoint.host && p.port == endpoint.port)
    parrot match {
      case None => ()
      case Some(paused) => {
        _pausedParrots -= paused
        _runningParrots += paused
        log.debug("unpaused a parrot %s:%d, paused=%d unpaused=%d",
          paused.host, paused.port, _pausedParrots.size, _runningParrots.size)
      }
    }
  }

  private[this] def pauseParrot(endpoint: Endpoint) {
    val parrot = _runningParrots.find(p => p.host == endpoint.host && p.port == endpoint.port)
    parrot match {
      case None => ()
      case Some(paused) => {
        _runningParrots -= paused
        _pausedParrots += paused
        log.debug("paused a parrot %s:%d, paused=%d unpaused=%d",
          paused.host, paused.port, _pausedParrots.size, _runningParrots.size)
      }
    }
  }

  private[this] def isParrotConnected(parrot: RemoteParrot) = {
    parrot.isConnected
  }

  private[this] def connectParrotsFromConfig(config: ParrotCommonConfig): List[RemoteParrot] = {
    var result = List[RemoteParrot]()
    config.parrotHosts foreach { host =>
      result = connectParrot(host, config.parrotPort) match {
        case Some(parrot) => {
          _runningParrots += parrot
          parrot :: result
        }
        case None => result
      }
    }
    result
  }

  private[this] def connectParrotsFromZookeeper(config: ParrotCommonConfig): List[RemoteParrot] = {
    var result = List[RemoteParrot]()

    discovery.monitor(this)
    waitForMembers(timeout = Duration(10000, TimeUnit.MILLISECONDS))

    instances foreach { instance =>
      val endpoint = instance.getServiceEndpoint
      result = connectParrot(endpoint.host, endpoint.port) match {
        case Some(parrot) => {
          if (instance.getStatus == Status.STOPPING) {
            log.info("Found a paused Parrot on startup: %s:%d", endpoint.host, endpoint.port)
            _pausedParrots += parrot
            result
          } else {
            parrot :: result
          }
        }
        case None => result
      }
    }
    result
  }

  private[this] def connectParrot(host: String, port: Int) = {
    log.info("Connecting to parrot server at %s:%d", host, port)

    var result: Option[RemoteParrot] = None
    try {
      result = Some(
        new RemoteParrot(host, new InternalCounter(), host, port, getConfig.finagleTimeout))
    } catch {
      case t: Throwable =>
        log.error(t, "Error connecting to %s %d: %s", host, port, t.getMessage)
        t.printStackTrace
    }
    result
  }

  def waitForMembers(timeout: Duration) {
    if (instances.isEmpty) {
      members.await(timeout.inMilliseconds, TimeUnit.MILLISECONDS)
    }
  }

  def onChange(hostSet: ImmutableSet[ServiceInstance]) {
    handleClusterEvent(hostSet)
    members.countDown()
    instances = Set.empty ++ hostSet.asScala
  }

  /**
   * This is here to support testing in isolation.
   * ParrotClusterSpec overrides it in ways that either prevent
   * the side effect of trying to connected to a parrot, or create
   * new side effects that allow for the appearance of synchronous
   * network logic when the calls are really async.
   */
  def handleClusterEvent(set: ImmutableSet[ServiceInstance]) {
    updateParrots(set)
  }

  def start(port: Int) {
    log.logLazy(Level.DEBUG, "starting ParrotClusterImpl")
    config.get.zkHostName foreach { host => discovery.join(port, 
        config.get.runtime.get.arguments.get("shardId").get.toInt)
    }
  }

  def shutdown() {
    log.logLazy(Level.DEBUG, "ParrotClusterImpl: shutting down")
    config.get.zkHostName foreach { host => discovery.shutdown() }

    val allParrots = parrots
    allParrots foreach { parrot =>
      try {
        parrot.shutdown()
        log.info("connection to parrot server %s successfully shut down".format(parrot.name))
      } catch {
        case t: Throwable => log.error(t, "Error shutting down Parrot: %s", t.getClass.getName)
      }
    }
    _runningParrots.clear()
    _pausedParrots.clear()
  }

  def pause() {
    discovery.pause()
  }

  def resume() {
    discovery.resume()
  }
}
