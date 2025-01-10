package gateway.service.proxy;

import gateway.service.logging.LogLogger;
import gateway.service.utils.Common;
import gateway.service.utils.Constants;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import org.jetbrains.annotations.NotNull;

public class NettyServer {
  private static final LogLogger logger = LogLogger.getLogger(NettyServer.class);

  public void start() throws Exception {
    final EventLoopGroup bossGroup = new NioEventLoopGroup(Constants.BOSS_GROUP_THREADS);
    final EventLoopGroup workerGroup = new NioEventLoopGroup(Constants.WORKER_GROUP_THREADS);

    try {
      final ServerBootstrap serverBootstrap = new ServerBootstrap();
      serverBootstrap
          .group(bossGroup, workerGroup)
          .channel(NioServerSocketChannel.class)
          .childHandler(
              new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(@NotNull final SocketChannel socketChannel)
                    throws Exception {
                  socketChannel
                      .pipeline()
                      .addLast(new HttpServerCodec())
                      .addLast(new HttpObjectAggregator(Constants.MAX_CONTENT_LENGTH))
                      .addLast(new GatewayRequestHandler(workerGroup));
                }
              });

      final ChannelFuture channelFuture =
          serverBootstrap
              .bind(
                  Integer.parseInt(
                      Common.getSystemEnvProperty(Constants.ENV_PORT, Constants.ENV_PORT_DEFAULT)))
              .sync();

      logger.info("Gateway Server Started on Port 8000...");
      channelFuture.channel().closeFuture().sync();
    } finally {
      workerGroup.shutdownGracefully();
      bossGroup.shutdownGracefully();
      logger.info("Gateway Server Stopped...");
    }
  }
}
