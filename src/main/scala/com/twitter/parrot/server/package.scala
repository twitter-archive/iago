package com.twitter.parrot

import com.twitter.parrot.config.ParrotServerConfig

package object server {
  type ParrotTransportFactory[Req <: ParrotRequest, Rep] = ((ParrotServerConfig[Req, Rep]) => ParrotTransport[Req, Rep])
}

