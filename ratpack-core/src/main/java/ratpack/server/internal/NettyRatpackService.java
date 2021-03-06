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

package ratpack.server.internal;

import com.google.common.util.concurrent.AbstractIdleService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import ratpack.launch.LaunchConfig;
import ratpack.server.Stopper;
import ratpack.util.Transformer;

import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

import static ratpack.util.ExceptionUtils.uncheck;

public class NettyRatpackService extends AbstractIdleService implements RatpackService {

  private final Logger logger = Logger.getLogger(getClass().getName());

  private final LaunchConfig launchConfig;
  private final Transformer<Stopper, ChannelInitializer<SocketChannel>> channelInitializerTransformer;

  private InetSocketAddress boundAddress;
  private Channel channel;
  private EventLoopGroup group;

  public NettyRatpackService(LaunchConfig launchConfig, Transformer<Stopper, ChannelInitializer<SocketChannel>> channelInitializerTransformer) {
    this.launchConfig = launchConfig;
    this.channelInitializerTransformer = channelInitializerTransformer;
  }

  @Override
  protected void startUp() throws Exception {
    Stopper stopper = new Stopper() {
      @Override
      public void stop() {
        try {
          NettyRatpackService.this.stop();
        } catch (Exception e) {
          throw uncheck(e);
        }
      }
    };

    ServerBootstrap bootstrap = new ServerBootstrap();
    group = new NioEventLoopGroup(launchConfig.getMainThreads(), new DefaultThreadFactory("ratpack-group", Thread.MAX_PRIORITY));

    ChannelInitializer<SocketChannel> channelInitializer = channelInitializerTransformer.transform(stopper);

    bootstrap
      .group(group)
      .channel(NioServerSocketChannel.class)
      .childHandler(channelInitializer)
      .childOption(ChannelOption.ALLOCATOR, launchConfig.getBufferAllocator())
      .childOption(ChannelOption.TCP_NODELAY, true)
      .option(ChannelOption.SO_REUSEADDR, true)
      .option(ChannelOption.SO_BACKLOG, 1024)
      .option(ChannelOption.ALLOCATOR, launchConfig.getBufferAllocator());

    try {
      channel = bootstrap.bind(buildSocketAddress()).sync().channel();
    } catch (Exception e) {
      partialShutdown();
      throw e;
    }
    boundAddress = (InetSocketAddress) channel.localAddress();

    if (logger.isLoggable(Level.INFO)) {
      logger.info(String.format("Ratpack started for http://%s:%s", getBindHost(), getBindPort()));
    }
  }

  @Override
  protected void shutDown() throws Exception {
    channel.close();
    partialShutdown();
  }

  private void partialShutdown() {
    group.shutdownGracefully();
    launchConfig.getBackgroundExecutorService().shutdown();
  }

  @Override
  public String getScheme() {
    return launchConfig.getSSLContext() == null ? "http" : "https";
  }

  public int getBindPort() {
    return boundAddress == null ? -1 : boundAddress.getPort();
  }

  public String getBindHost() {
    if (boundAddress == null) {
      return null;
    } else {
      return InetSocketAddressBackedBindAddress.determineHost(boundAddress);
    }
  }

  private InetSocketAddress buildSocketAddress() {
    return (launchConfig.getAddress() == null) ? new InetSocketAddress(launchConfig.getPort()) : new InetSocketAddress(launchConfig.getAddress(), launchConfig.getPort());
  }

}
