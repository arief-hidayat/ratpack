/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ratpack.websocket

import ratpack.test.internal.RatpackGroovyDslSpec
import spock.util.concurrent.BlockingVariable

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

import static ratpack.websocket.WebSockets.websocket

class WebsocketSpec extends RatpackGroovyDslSpec {

  def "can send and receive websockets"() {
    when:
    def closing = new BlockingVariable<WebSocketClose<Integer>>()
    def serverReceived = new LinkedBlockingQueue<WebSocketMessage<Integer>>()
    WebSocket ws

    app {
      handlers {
        get {
          websocket(context) {
            ws = it
            2
          }.onClose {
            closing.set(it)
          }.onMessage {
            serverReceived.put it
            it.connection.send(it.text.toUpperCase())
          }.connect()
        }
      }
    }

    and:
    startServerIfNeeded()
    def client = openWsClient()

    then:
    client.connectBlocking()
    client.send("foo")

    and:
    with(serverReceived.poll(5, TimeUnit.SECONDS)) {
      text == "foo"
      openResult == 2
    }
    client.received.poll(5, TimeUnit.SECONDS) == "FOO"

    when:
    client.closeBlocking()

    then:
    with(closing.get()) {
      fromClient
      openResult == 2
    }

    //noinspection GroovyVariableNotAssigned
    !ws.open

    cleanup:
    client.closeBlocking()
  }

  def RecordingWebSocketClient openWsClient() {
    new RecordingWebSocketClient(new URI("ws://localhost:$server.bindPort"))
  }

  def "client receives error when exception thrown during server open"() {
    when:
    app {
      handlers {
        get {
          websocket(context) {
            throw new Exception("!")
          }.connect()
        }
      }
    }
    startServerIfNeeded()

    and:
    def client = openWsClient()
    client.connectBlocking()

    then:
    client.waitForClose()
    client.closeCode == 1011

    cleanup:
    client?.closeBlocking()

  }

}
