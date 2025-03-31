package examples.grpc;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.Service;
import io.vertx.grpc.server.ServiceBuilder;

import com.google.protobuf.Descriptors;

import java.util.LinkedList;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>Provides support for RPC methods implementations of the Streaming gRPC service.</p>
 *
 * <p>The following methods of this class should be overridden to provide an implementation of the service:</p>
 * <ul>
 *   <li>Source</li>
 *   <li>Sink</li>
 *   <li>Pipe</li>
 * </ul>
 */
public class StreamingService implements Streaming, Service {

  /**
   * Streaming service name.
   */
  public static final ServiceName SERVICE_NAME = ServiceName.create("examples.grpc", "Streaming");

  /**
   * Streaming service descriptor.
   */
  public static final Descriptors.ServiceDescriptor SERVICE_DESCRIPTOR = Docs.getDescriptor().findServiceByName("Streaming");

  @Override
  public ServiceName name() {
    return SERVICE_NAME;
  }

  @Override
  public Descriptors.ServiceDescriptor descriptor() {
    return SERVICE_DESCRIPTOR;
  }

  @Override
  public void bind(GrpcServer server) {
    builder().bind(all()).build().bind(server);
  }

  /**
   * Source protobuf RPC server service method.
   */
  public static final ServiceMethod<examples.grpc.Empty, examples.grpc.Item> Source = ServiceMethod.server(
    SERVICE_NAME,
    "Source",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.grpc.Empty.parser()));

  /**
   * Sink protobuf RPC server service method.
   */
  public static final ServiceMethod<examples.grpc.Item, examples.grpc.Empty> Sink = ServiceMethod.server(
    SERVICE_NAME,
    "Sink",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.grpc.Item.parser()));

  /**
   * Pipe protobuf RPC server service method.
   */
  public static final ServiceMethod<examples.grpc.Item, examples.grpc.Item> Pipe = ServiceMethod.server(
    SERVICE_NAME,
    "Pipe",
    GrpcMessageEncoder.encoder(),
    GrpcMessageDecoder.decoder(examples.grpc.Item.parser()));

  /**
   * @return a mutable list of the known protobuf RPC server service methods.
   */
  public static java.util.List<ServiceMethod<?, ?>> all() {
    java.util.List<ServiceMethod<?, ?>> all = new java.util.ArrayList<>();
    all.add(Source);
    all.add(Sink);
    all.add(Pipe);
    return all;
  }

  /**
   * Json server service methods.
   */
  public static final class Json {

    /**
     * Source json RPC server service method.
     */
    public static final ServiceMethod<examples.grpc.Empty, examples.grpc.Item> Source = ServiceMethod.server(
      SERVICE_NAME,
      "Source",
      GrpcMessageEncoder.json(),
      GrpcMessageDecoder.json(() -> examples.grpc.Empty.newBuilder()));

    /**
     * Sink json RPC server service method.
     */
    public static final ServiceMethod<examples.grpc.Item, examples.grpc.Empty> Sink = ServiceMethod.server(
      SERVICE_NAME,
      "Sink",
      GrpcMessageEncoder.json(),
      GrpcMessageDecoder.json(() -> examples.grpc.Item.newBuilder()));

    /**
     * Pipe json RPC server service method.
     */
    public static final ServiceMethod<examples.grpc.Item, examples.grpc.Item> Pipe = ServiceMethod.server(
      SERVICE_NAME,
      "Pipe",
      GrpcMessageEncoder.json(),
      GrpcMessageDecoder.json(() -> examples.grpc.Item.newBuilder()));

    /**
     * @return a mutable list of the known json RPC server service methods.
     */
    public static java.util.List<ServiceMethod<?, ?>> all() {
      java.util.List<ServiceMethod<?, ?>> all = new java.util.ArrayList<>();
      all.add(Source);
      all.add(Sink);
      all.add(Pipe);
      return all;
    }
  }

  /**
   * Transcoded server service methods.
   */
  public static final class Transcoding {

    /**
     * @return a mutable list of the known transcoded RPC server service methods.
     */
    public static java.util.List<ServiceMethod<?, ?>> all() {
      java.util.List<ServiceMethod<?, ?>> all = new java.util.ArrayList<>();
      return all;
    }
  }


  /**
   * Override this method to implement the Source RPC.
   */
  public Future<ReadStream<examples.grpc.Item>> source(examples.grpc.Empty request) {
    throw new UnsupportedOperationException("Not implemented");
  }

  protected void source(examples.grpc.Empty request, WriteStream<examples.grpc.Item> response) {
    source(request)
      .onComplete(ar -> {
        if (ar.succeeded()) {
          ReadStream<examples.grpc.Item> stream = ar.result();
          stream.pipeTo(response);
        } else {
          // Todo
        }
      });
  }

  /**
   * Override this method to implement the Sink RPC.
   */
  public Future<examples.grpc.Empty> sink(ReadStream<examples.grpc.Item> request) {
    throw new UnsupportedOperationException("Not implemented");
  }

  protected void sink(ReadStream<examples.grpc.Item> request, Promise<examples.grpc.Empty> response) {
    sink(request)
      .onSuccess(msg -> response.complete(msg))
      .onFailure(error -> response.fail(error));
  }

  /**
   * Override this method to implement the Pipe RPC.
   */
  public Future<ReadStream<examples.grpc.Item>> pipe(ReadStream<examples.grpc.Item> request) {
    throw new UnsupportedOperationException("Not implemented");
  }

  protected void pipe(ReadStream<examples.grpc.Item> request, WriteStream<examples.grpc.Item> response) {
    pipe(request)
      .onComplete(ar -> {
        if (ar.succeeded()) {
          ReadStream<examples.grpc.Item> stream = ar.result();
          stream.pipeTo(response);
        } else {
          // Todo
        }
      });
  }

  private <Req, Resp> Handler<io.vertx.grpc.server.GrpcServerRequest<Req, Resp>> resolveHandler(ServiceMethod<Req, Resp> serviceMethod) {
    if (Source == serviceMethod || Json.Source == serviceMethod) {
      Handler<io.vertx.grpc.server.GrpcServerRequest<examples.grpc.Empty, examples.grpc.Item>> handler = StreamingService.this::handle_source;
      Handler<?> handler2 = handler;
      return (Handler<io.vertx.grpc.server.GrpcServerRequest<Req, Resp>>) handler2;
    }
    if (Sink == serviceMethod || Json.Sink == serviceMethod) {
      Handler<io.vertx.grpc.server.GrpcServerRequest<examples.grpc.Item, examples.grpc.Empty>> handler = StreamingService.this::handle_sink;
      Handler<?> handler2 = handler;
      return (Handler<io.vertx.grpc.server.GrpcServerRequest<Req, Resp>>) handler2;
    }
    if (Pipe == serviceMethod || Json.Pipe == serviceMethod) {
      Handler<io.vertx.grpc.server.GrpcServerRequest<examples.grpc.Item, examples.grpc.Item>> handler = StreamingService.this::handle_pipe;
      Handler<?> handler2 = handler;
      return (Handler<io.vertx.grpc.server.GrpcServerRequest<Req, Resp>>) handler2;
    }
    return null;
  }

  /**
   * @return a free form builder that gives the opportunity to bind only certain methods of a service
   */
  public Builder builder() {
    return new Builder();
  }

  /**
   * Service builder.
   */
  public class Builder implements ServiceBuilder {

    private final List<ServiceMethod<?, ?>> serviceMethods = new ArrayList<>();

    private void validate() {
      for (ServiceMethod<?, ?> serviceMethod : serviceMethods) {
        if (resolveHandler(serviceMethod) == null) {
          throw new IllegalArgumentException("Invalid service method:" + serviceMethod);
        }
      }
    }

    private <Req, Resp> Handler<io.vertx.grpc.server.GrpcServerRequest<Req, Resp>> resolveHandler(ServiceMethod<Req, Resp> serviceMethod) {
      if (Source == serviceMethod || Json.Source == serviceMethod) {
        Handler<io.vertx.grpc.server.GrpcServerRequest<examples.grpc.Empty, examples.grpc.Item>> handler = StreamingService.this::handle_source;
        Handler<?> handler2 = handler;
        return (Handler<io.vertx.grpc.server.GrpcServerRequest<Req, Resp>>) handler2;
      }
      if (Sink == serviceMethod || Json.Sink == serviceMethod) {
        Handler<io.vertx.grpc.server.GrpcServerRequest<examples.grpc.Item, examples.grpc.Empty>> handler = StreamingService.this::handle_sink;
        Handler<?> handler2 = handler;
        return (Handler<io.vertx.grpc.server.GrpcServerRequest<Req, Resp>>) handler2;
      }
      if (Pipe == serviceMethod || Json.Pipe == serviceMethod) {
        Handler<io.vertx.grpc.server.GrpcServerRequest<examples.grpc.Item, examples.grpc.Item>> handler = StreamingService.this::handle_pipe;
        Handler<?> handler2 = handler;
        return (Handler<io.vertx.grpc.server.GrpcServerRequest<Req, Resp>>) handler2;
      }
      return null;
    }

    /**
     * Throws {@code UnsupportedOperationException}.
     */
    public <Req, Resp> ServiceBuilder bind(ServiceMethod<Req, Resp> serviceMethod, Handler<GrpcServerRequest<Req, Resp>> handler) {
      throw new UnsupportedOperationException();
    }

    /**
     * @return this builder
     */
    public Builder bind(List<ServiceMethod<?, ?>> methods) {
      serviceMethods.addAll(methods);
      return this;
    }

    /**
     * @return this builder
     */
    public Builder bind(ServiceMethod<?, ?>... methods) {
      return bind(java.util.Arrays.asList(methods));
    }

    public Service build() {
      // Defensive copy
      List<ServiceMethod<?, ?>> serviceMethods = new ArrayList<>(Builder.this.serviceMethods);
      return new Service() {
        public ServiceName name() {
          return SERVICE_NAME;
        }
        public Descriptors.ServiceDescriptor descriptor() {
          return SERVICE_DESCRIPTOR;
        }
        /**
         * Bind the contained service methods to the {@code server}.
         */
        public void bind(GrpcServer server) {
          for (ServiceMethod<?, ?> serviceMethod : serviceMethods) {
            bindHandler(serviceMethod, server);
          }
        }
        private <Req, Resp> void bindHandler(ServiceMethod<Req, Resp> serviceMethod, GrpcServer server) {
          Handler<io.vertx.grpc.server.GrpcServerRequest<Req, Resp>> handler = resolveHandler(serviceMethod);
          server.callHandler(serviceMethod, handler);
        }
      };
    }
  }

  private void handle_source(io.vertx.grpc.server.GrpcServerRequest<examples.grpc.Empty, examples.grpc.Item> request) {
    request.handler(msg -> {
      try {
        source(msg, request.response());
      } catch (RuntimeException err) {
        request.response().status(GrpcStatus.INTERNAL).end();
      }
    });
  }

  private void handle_sink(io.vertx.grpc.server.GrpcServerRequest<examples.grpc.Item, examples.grpc.Empty> request) {
    Promise<examples.grpc.Empty> promise = Promise.promise();
    promise.future()
      .onFailure(err -> request.response().status(GrpcStatus.INTERNAL).end())
      .onSuccess(resp -> request.response().end(resp));
    try {
      sink(request, promise);
    } catch (RuntimeException err) {
      promise.tryFail(err);
    }
  }

  private void handle_pipe(io.vertx.grpc.server.GrpcServerRequest<examples.grpc.Item, examples.grpc.Item> request) {
    try {
      pipe(request, request.response());
    } catch (RuntimeException err) {
      request.response().status(GrpcStatus.INTERNAL).end();
    }
  }
}
