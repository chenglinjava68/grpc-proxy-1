/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.codahale.gprcproxy;

import com.codahale.grpcproxy.helloworld.GreeterGrpc;
import com.codahale.grpcproxy.helloworld.HelloReply;
import com.codahale.grpcproxy.helloworld.HelloRequest;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import java.io.File;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import javax.net.ssl.SSLException;

/**
 * A gRPC client. This could be in any language.
 */
public class HelloWorldClient {

  private static final Logger logger = Logger.getLogger(HelloWorldClient.class.getName());

  private final ManagedChannel channel;
  private final GreeterGrpc.GreeterBlockingStub blockingStub;

  private HelloWorldClient(String host, int port) throws SSLException {
    final File tlsCert = Paths.get("cert.crt").toFile();
    final File tlsKey = Paths.get("cert.key").toFile();
    final SslContextBuilder builder = SslContextBuilder.forClient();
    final SslContext sslContext = GrpcSslContexts.configure(builder, SslProvider.OPENSSL)
                                                 .trustManager(tlsCert)
                                                 .keyManager(tlsCert, tlsKey)
                                                 .build();

    this.channel = NettyChannelBuilder.forAddress(host, port).sslContext(sslContext).build();
    this.blockingStub = GreeterGrpc.newBlockingStub(channel);
  }

  public static void main(String[] args) throws Exception {
    /* Access a service running on the local machine on port 50051 */
    final HelloWorldClient client = new HelloWorldClient("localhost", 50051);
    try {
      final int requests = 10_000;
      System.out.println("sending " + requests + " requests in parallel");
      final Instant start = Instant.now();
      final long greetings = IntStream.range(0, requests)
                                      .parallel()
                                      .mapToObj(client::greet)
                                      .count();
      System.out.println(greetings + " requests in " + Duration.between(start, Instant.now()));
    } finally {
      client.shutdown();
    }
  }

  private void shutdown() throws InterruptedException {
    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
  }

  private String greet(int i) {
    final HelloRequest request = HelloRequest.newBuilder().setName("world " + i).build();
    try {
      final HelloReply response = blockingStub.sayHello(request);
      return response.getMessage();
    } catch (StatusRuntimeException e) {
      logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
      return null;
    }
  }
}
