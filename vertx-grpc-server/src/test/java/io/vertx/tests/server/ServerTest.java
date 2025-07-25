/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.server;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.grpc.common.GrpcHeaderNames;
import io.vertx.grpc.common.GrpcMessage;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.WireFormat;
import io.vertx.grpc.common.impl.GrpcMessageImpl;
import io.vertx.tests.common.grpc.*;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class ServerTest extends ServerTestBase {

  static final int NUM_ITEMS = 128;

  @Test
  public void testUnary(TestContext should) {
    testUnary(should, "identity", "identity");
  }

  @Test
  public void testUnaryDecompression(TestContext should) {
    testUnary(should, "gzip", "identity");
  }

  @Test
  public void testUnaryCompression(TestContext should) {
    testUnary(should, "identity", "gzip");
  }

  protected void testUnary(TestContext should, String requestEncoding, String responseEncoding) {
    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();

    AtomicReference<String> responseGrpcEncoding = new AtomicReference<>();
    TestServiceGrpc.TestServiceBlockingStub stub = TestServiceGrpc.newBlockingStub(ClientInterceptors.intercept(channel, new ClientInterceptor() {
        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
          return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
              super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener) {
                @Override
                public void onHeaders(Metadata headers) {
                  responseGrpcEncoding.set(headers.get(Metadata.Key.of("grpc-encoding", io.grpc.Metadata.ASCII_STRING_MARSHALLER)));
                  super.onHeaders(headers);
                }
              }, headers);
            }
          };
        }
      }))
      .withCompression(requestEncoding);
    Request request = Request.newBuilder().setName("Julien").build();
    Reply res = stub.unary(request);
    should.assertEquals("Hello Julien", res.getMessage());
    if (!responseEncoding.equals("identity")) {
      should.assertEquals(responseEncoding, responseGrpcEncoding.get());
    }
  }

  public void testStatusUnary(TestContext should, Status expectedStatus, String expectedStatusMessage) {
    testStatusUnary(should, expectedStatus, expectedStatusMessage, null);
  }

  public void testStatusUnary(TestContext should, Status expectedStatus, String expectedStatusMessage, MultiMap expectedTrailers) {
    Request request = Request.newBuilder().setName("Julien").build();
    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();
    TestServiceGrpc.TestServiceBlockingStub stub = TestServiceGrpc.newBlockingStub(channel);
    try {
      stub.unary(request);
      should.fail();
    } catch (StatusRuntimeException e) {
      should.assertEquals(expectedStatus.getCode(), e.getStatus().getCode());
      should.assertEquals(expectedStatusMessage, e.getStatus().getDescription());
      if (expectedTrailers != null) {
        Metadata trailers = e.getTrailers();
        should.assertNotNull(trailers, "No trailers provided in response");
        for (Map.Entry<String, String> expectedTrailer : expectedTrailers.entries()) {
          Metadata.Key key = Metadata.Key.of(expectedTrailer.getKey(), Metadata.ASCII_STRING_MARSHALLER);
          should.assertEquals(expectedTrailer.getValue(), trailers.get(key));
        }
      }

    }
  }

  public void testStatusStreaming(TestContext should, Status expectedStatus, String... expectedReplies) {
    channel = ManagedChannelBuilder.forAddress( "localhost", port)
      .usePlaintext()
      .build();
    TestServiceGrpc.TestServiceBlockingStub stub = TestServiceGrpc.newBlockingStub(channel);
    List<String> replies = new ArrayList<>();
    try {
      Iterator<Reply> iterator = stub.source(Empty.getDefaultInstance());
      while (iterator.hasNext()) {
        Reply next = iterator.next();
        replies.add(next.getMessage());
      }
      should.fail();
    } catch (StatusRuntimeException e) {
      should.assertEquals(expectedStatus, e.getStatus());
      should.assertEquals(Arrays.asList(expectedReplies), replies);
    }
  }

  @Test
  public void testServerStreaming(TestContext should) {
    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();
    TestServiceGrpc.TestServiceBlockingStub stub = TestServiceGrpc.newBlockingStub(channel);

    List<String> items = new ArrayList<>();
    stub.source(Empty.newBuilder().build()).forEachRemaining(item -> items.add(item.getMessage()));
    List<String> expected = IntStream.rangeClosed(0, NUM_ITEMS - 1).mapToObj(val -> "the-value-" + val).collect(Collectors.toList());
    should.assertEquals(expected, items);
  }

  @Test
  public void testClientStreaming(TestContext should) throws Exception {
    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();
    TestServiceGrpc.TestServiceStub stub = TestServiceGrpc.newStub(channel);

    Async test = should.async();
    StreamObserver<Request> items = stub.sink(new StreamObserver<Empty>() {
      @Override
      public void onNext(Empty value) {
      }
      @Override
      public void onError(Throwable t) {
        should.fail(t);
      }
      @Override
      public void onCompleted() {
        test.complete();
      }
    });
    for (int i = 0; i < NUM_ITEMS; i++) {
      items.onNext(Request.newBuilder().setName("the-value-" + i).build());
      Thread.sleep(10);
    }
    items.onCompleted();
  }

  @Test
  public void testClientStreamingCompletedBeforeHalfClose(TestContext should) {
    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();
    TestServiceGrpc.TestServiceStub stub = TestServiceGrpc.newStub(channel);

    Async test = should.async();
    StreamObserver<Request> items = stub.sink(new StreamObserver<Empty>() {
      @Override
      public void onNext(Empty value) {
        should.fail();
      }
      @Override
      public void onError(Throwable t) {
        test.complete();
      }
      @Override
      public void onCompleted() {
        should.fail();
      }
    });
    items.onNext(Request.newBuilder().setName("the-value").build());
  }

  @Test
  public void testBidiStreaming(TestContext should) throws Exception {
    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();
    TestServiceGrpc.TestServiceStub stub = TestServiceGrpc.newStub(channel);

    Async test = should.async();
    List<String> items = new ArrayList<>();
    StreamObserver<Request> writer = stub.pipe(new StreamObserver<Reply>() {
      @Override
      public void onNext(Reply item) {
        items.add(item.getMessage());
      }
      @Override
      public void onError(Throwable t) {
        should.fail(t);
      }
      @Override
      public void onCompleted() {
        test.complete();
      }
    });
    for (int i = 0; i < NUM_ITEMS; i++) {
      writer.onNext(Request.newBuilder().setName("the-value-" + i).build());
      Thread.sleep(10);
    }
    writer.onCompleted();
    test.awaitSuccess(20_000);
    List<String> expected = IntStream.rangeClosed(0, NUM_ITEMS - 1).mapToObj(val -> "the-value-" + val).collect(Collectors.toList());
    should.assertEquals(expected, items);
  }

  @Test
  public void testBidiStreamingCompletedBeforeHalfClose(TestContext should) throws Exception {
    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();
    TestServiceGrpc.TestServiceStub stub = TestServiceGrpc.newStub(channel);

    Async test = should.async();
    StreamObserver<Request> writer = stub.pipe(new StreamObserver<Reply>() {
      @Override
      public void onNext(Reply item) {
        should.fail();
      }
      @Override
      public void onError(Throwable t) {
        should.fail(t);
      }
      @Override
      public void onCompleted() {
        test.complete();
      }
    });
    writer.onNext(Request.newBuilder().setName("the-value").build());
  }

  protected AtomicInteger testMetadataStep;

  @Test
  public void testMetadata(TestContext should) {

    testMetadataStep = new AtomicInteger();

    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();

    ClientInterceptor interceptor = new ClientInterceptor() {
      @Override
      public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
          @Override
          public void start(Listener<RespT> responseListener, Metadata headers) {
            headers.put(Metadata.Key.of("custom_request_header", io.grpc.Metadata.ASCII_STRING_MARSHALLER), "custom_request_header_value");
            headers.put(Metadata.Key.of("custom_request_header-bin", Metadata.BINARY_BYTE_MARSHALLER), new byte[]{0,1,2});
            headers.put(Metadata.Key.of("grpc-custom_request_header", io.grpc.Metadata.ASCII_STRING_MARSHALLER), "grpc-custom_request_header_value");
            headers.put(Metadata.Key.of("grpc-custom_request_header-bin", io.grpc.Metadata.BINARY_BYTE_MARSHALLER), new byte[]{2,1,0});
            super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener) {
              @Override
              public void onHeaders(Metadata headers) {
                should.assertEquals("custom_response_header_value", headers.get(Metadata.Key.of("custom_response_header", Metadata.ASCII_STRING_MARSHALLER)));
                assertEquals(should, new byte[] { 0,1,2 }, headers.get(Metadata.Key.of("custom_response_header-bin", Metadata.BINARY_BYTE_MARSHALLER)));
                should.assertEquals("grpc-custom_response_header_value", headers.get(Metadata.Key.of("grpc-custom_response_header", Metadata.ASCII_STRING_MARSHALLER)));
                assertEquals(should, new byte[] { 2,1,0 }, headers.get(Metadata.Key.of("grpc-custom_response_header-bin", Metadata.BINARY_BYTE_MARSHALLER)));
                int step = testMetadataStep.getAndIncrement();
                should.assertTrue(step == 2 || step == 3, "Was expected " + step + " 3 or " + step + " == 4");
                super.onHeaders(headers);
              }
              @Override
              public void onClose(Status status, Metadata trailers) {
                should.assertEquals("custom_response_trailer_value", trailers.get(Metadata.Key.of("custom_response_trailer", Metadata.ASCII_STRING_MARSHALLER)));
                assertEquals(should, new byte[] { 0,1,2 }, trailers.get(Metadata.Key.of("custom_response_trailer-bin", Metadata.BINARY_BYTE_MARSHALLER)));
                should.assertEquals("grpc-custom_response_trailer_value", trailers.get(Metadata.Key.of("grpc-custom_response_trailer", Metadata.ASCII_STRING_MARSHALLER)));
                assertEquals(should, new byte[] { 2,1,0 }, trailers.get(Metadata.Key.of("grpc-custom_response_trailer-bin", Metadata.BINARY_BYTE_MARSHALLER)));
                should.assertEquals(4, testMetadataStep.getAndIncrement());
                super.onClose(status, trailers);
              }
            }, headers);
          }
        };
      }
    };

    TestServiceGrpc.TestServiceBlockingStub stub = TestServiceGrpc.newBlockingStub(ClientInterceptors.intercept(channel, interceptor));
    Request request = Request.newBuilder().setName("Julien").build();
    Reply res = stub.unary(request);
    should.assertEquals("Hello Julien", res.getMessage());

    should.assertEquals(5, testMetadataStep.get());
  }

  @Test
  public void testHandleCancel(TestContext should) {
    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();
    TestServiceGrpc.TestServiceStub stub = TestServiceGrpc.newStub(channel);

    Async latch = should.async();
    ClientCallStreamObserver<Request> items = (ClientCallStreamObserver<Request>) stub.pipe(new StreamObserver<Reply>() {
      AtomicInteger count = new AtomicInteger();
      @Override
      public void onNext(Reply value) {
        if (count.getAndIncrement() == 0) {
          latch.complete();
        }
      }
      @Override
      public void onError(Throwable t) {
      }
      @Override
      public void onCompleted() {
      }
    });
    items.onNext(Request.newBuilder().setName("the-value").build());
    latch.awaitSuccess(10_000);
    items.cancel("cancelled", new Exception());
  }

  protected void testEarlyHeaders(TestContext should, GrpcStatus status, Runnable continuation) {
    channel = ManagedChannelBuilder.forAddress("localhost", port)
      .usePlaintext()
      .build();

    HttpClient client = vertx.createHttpClient(new HttpClientOptions()
      .setHttp2ClearTextUpgrade(false)
      .setProtocolVersion(HttpVersion.HTTP_2));
    Async async = should.async();
    client.request(HttpMethod.POST, port, "localhost", "/io.vertx.tests.common.grpc.tests.TestService/Unary")
      .onComplete(should.asyncAssertSuccess(req -> {
        req.putHeader(HttpHeaders.CONTENT_TYPE, "application/grpc");
        req.response().onComplete(should.asyncAssertSuccess(resp -> {
          should.assertNull(resp.getHeader(GrpcHeaderNames.GRPC_STATUS));
          resp.handler(buff -> {
            should.fail();
          });
          vertx.setTimer(200, id -> {
            resp.handler(null);
            resp.endHandler(v -> {
              MultiMap trailers = resp.trailers();
              should.assertEquals("" + status.code, trailers.get(GrpcHeaderNames.GRPC_STATUS));
              async.complete();
            });
            continuation.run();
          });
        }));
        GrpcMessage msg = TestConstants.REQUEST_ENC.encode(Request.newBuilder().setName("test").build(), WireFormat.PROTOBUF);
        req.end(GrpcMessageImpl.encode(msg));
      }));

    async.awaitSuccess();
  }

  @Test
  public void testTrailersOnly(TestContext should) {
    HttpClient client = vertx.createHttpClient(new HttpClientOptions()
      .setHttp2ClearTextUpgrade(false)
      .setProtocolVersion(HttpVersion.HTTP_2));
    Async async = should.async();
    client.request(HttpMethod.POST, port, "localhost", "/io.vertx.tests.common.grpc.tests.TestService/Source")
      .onComplete(should.asyncAssertSuccess(req -> {
        req.putHeader(HttpHeaders.CONTENT_TYPE, "application/grpc");
        req.response().onComplete(should.asyncAssertSuccess(resp -> {
          should.assertEquals("3", resp.getHeader("grpc-status"));
          resp.endHandler(v -> {
            should.assertNull(resp.getTrailer("grpc-status"));
            async.complete();
          });
        }));
        GrpcMessage msg = TestConstants.EMPTY_ENC.encode(Empty.getDefaultInstance(), WireFormat.PROTOBUF);
        req.end(GrpcMessageImpl.encode(msg));
      }));

    async.awaitSuccess();
  }

  @Test
  public void testDistinctHeadersAndTrailers(TestContext should) {
    HttpClient client = vertx.createHttpClient(new HttpClientOptions()
      .setHttp2ClearTextUpgrade(false)
      .setProtocolVersion(HttpVersion.HTTP_2));
    Async async = should.async();
    client.request(HttpMethod.POST, port, "localhost", "/io.vertx.tests.common.grpc.tests.TestService/Source")
      .onComplete(should.asyncAssertSuccess(req -> {
        req.putHeader(HttpHeaders.CONTENT_TYPE, "application/grpc");
        req.response().onComplete(should.asyncAssertSuccess(resp -> {
          should.assertNull(resp.getHeader("grpc-status"));
          resp.endHandler(v -> {
            should.assertEquals("0", resp.getTrailer("grpc-status"));
            async.complete();
          });
        }));
        GrpcMessage msg = TestConstants.EMPTY_ENC.encode(Empty.getDefaultInstance(), WireFormat.PROTOBUF);
        req.end(GrpcMessageImpl.encode(msg));
      }));

    async.awaitSuccess();
  }

  @Test
  public void testTimeoutOnServerBeforeSendingResponse(TestContext should) throws Exception {
    HttpClient client = vertx.createHttpClient(new HttpClientOptions()
      .setHttp2ClearTextUpgrade(false)
      .setProtocolVersion(HttpVersion.HTTP_2));
    Async async = should.async();
    client.request(HttpMethod.POST, port, "localhost", "/io.vertx.tests.common.grpc.tests.TestService/Unary")
      .onComplete(should.asyncAssertSuccess(req -> {
        req.putHeader(GrpcHeaderNames.GRPC_TIMEOUT, TimeUnit.SECONDS.toMillis(1) + "m");
        req.putHeader(HttpHeaders.CONTENT_TYPE, "application/grpc");
        req.response().onComplete(should.asyncAssertSuccess(resp -> {
          String status = resp.getHeader(GrpcHeaderNames.GRPC_STATUS);
          should.assertEquals(String.valueOf(GrpcStatus.DEADLINE_EXCEEDED.code), status);
          async.complete();
        }));
        GrpcMessage msg = TestConstants.REQUEST_ENC.encode(Request.newBuilder().setName("test").build(), WireFormat.PROTOBUF);
        req.end(GrpcMessageImpl.encode(msg));
      }));

    async.awaitSuccess();
  }

  protected static void assertEquals(TestContext should, byte[] expected, byte[] actual) {
    should.assertNotNull(actual);
    should.assertTrue(Arrays.equals(expected, actual));
  }

  protected static void assertEquals(TestContext should, byte[] expected, String actual) {
    should.assertNotNull(actual);
    should.assertTrue(Arrays.equals(expected, Base64.getDecoder().decode(actual)));
  }

  public void testJsonMessageFormat(TestContext should, String contentType) throws Exception {

    JsonObject helloReply = new JsonObject().put("message", "Hello Julien");
    JsonObject helloRequest = new JsonObject().put("name", "Julien");

    HttpClient client = vertx.createHttpClient(new HttpClientOptions()
      .setHttp2ClearTextUpgrade(false)
      .setProtocolVersion(HttpVersion.HTTP_2));
    Async async = should.async();
    client.request(HttpMethod.POST, port, "localhost", "/io.vertx.tests.common.grpc.tests.TestService/Unary")
      .onComplete(should.asyncAssertSuccess(req -> {
        req.putHeader(HttpHeaders.CONTENT_TYPE, contentType);
        req.response().onComplete(should.asyncAssertSuccess(resp -> {
          should.assertEquals(contentType, resp.getHeader(HttpHeaders.CONTENT_TYPE));
          resp.bodyHandler(msg -> {
            int len = msg.getInt(1);
            JsonObject actual = msg.getBuffer(5, 5 + len).toJsonObject();
            should.assertEquals(helloReply, actual);
            async.complete();
          });
        }));
        Buffer payload = helloRequest.toBuffer();
        req.end(Buffer.buffer().appendByte((byte)0).appendInt(payload.length()).appendBuffer(payload));
      }));

    async.awaitSuccess();
  }
}
