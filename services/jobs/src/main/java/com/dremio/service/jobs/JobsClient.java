/*
 * Copyright (C) 2017-2019 Dremio Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dremio.service.jobs;

import javax.inject.Provider;

import org.apache.arrow.flight.FlightClient;
import org.apache.arrow.flight.FlightGrpcUtils;
import org.apache.arrow.memory.BufferAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dremio.common.AutoCloseables;
import com.dremio.exec.proto.CoordinationProtos;
import com.dremio.service.Service;
import com.dremio.service.conduit.client.ConduitProvider;
import com.dremio.service.grpc.GrpcChannelBuilderFactory;
import com.dremio.service.job.ChronicleGrpc;
import com.dremio.service.job.ChronicleGrpc.ChronicleBlockingStub;
import com.dremio.service.job.JobsServiceGrpc;
import com.dremio.service.job.JobsServiceGrpc.JobsServiceBlockingStub;
import com.dremio.service.job.JobsServiceGrpc.JobsServiceStub;

import io.grpc.ManagedChannel;

/**
 * Client that maintains the lifecycle of a channel over which job RPC requests are made to the jobs service. After the
 * client is {@link #start started}, the {@link #getBlockingStub blocking stub} and {@link #getAsyncStub async stub}
 * are available.
 */
public class JobsClient implements Service {
  private static final Logger logger = LoggerFactory.getLogger(JobsClient.class);

  private final GrpcChannelBuilderFactory grpcFactory;
  private final BufferAllocator allocator;
  private final Provider<Integer> portProvider;
  private final ConduitProvider conduitProvider;
  private final Provider<CoordinationProtos.NodeEndpoint> selfEndpoint;

  private ManagedChannel channel;
  private JobsServiceBlockingStub blockingStub;
  private JobsServiceStub asyncStub;
  private ChronicleBlockingStub chronicleBlockingStub;
  private FlightClient flightClient;

  JobsClient(GrpcChannelBuilderFactory grpcFactory, Provider<BufferAllocator> allocator,
             Provider<Integer> portProvider, final Provider<CoordinationProtos.NodeEndpoint> selfEndpoint,
             ConduitProvider conduitProvider) {
    this.grpcFactory = grpcFactory;
    this.allocator = allocator.get().newChildAllocator(getClass().getName(), 0, Long.MAX_VALUE);
    this.portProvider = portProvider;
    this.selfEndpoint = selfEndpoint;
    this.conduitProvider = conduitProvider;
  }

  @Override
  public void start() {
    channel = this.conduitProvider.getOrCreateChannel(selfEndpoint.get());
    blockingStub = JobsServiceGrpc.newBlockingStub(channel);
    asyncStub = JobsServiceGrpc.newStub(channel);
    flightClient = FlightGrpcUtils.createFlightClient(allocator, channel);
    chronicleBlockingStub = ChronicleGrpc.newBlockingStub(channel);

    logger.info("Job channel created to authority '{}'", channel.authority());
  }


  @Override
  public void close() throws Exception {
    AutoCloseables.close(flightClient, allocator);
    if (channel != null) {
      channel.shutdown();
    }
  }

  /**
   * Get the blocking stub to make RPC requests to jobs service.
   *
   * @return blocking stub
   */
  public JobsServiceGrpc.JobsServiceBlockingStub getBlockingStub() {
    return blockingStub;
  }

  /**
   * Get the async stub to make RPC requests to jobs service.
   *
   * @return async stub
   */
  public JobsServiceGrpc.JobsServiceStub getAsyncStub() {
    return asyncStub;
  }

  /**
   * Get the Arrow Flight Client for data requests to jobs service.
   *
   * @return Flight Client
   */
  public FlightClient getFlightClient() {
    return flightClient;
  }

  /**
   * Get the blocking stub to make RPC requests to Chronicle service
   * @return blocking stub
   */
  public ChronicleGrpc.ChronicleBlockingStub getChronicleBlockingStub() {
    return chronicleBlockingStub;
  }
}
