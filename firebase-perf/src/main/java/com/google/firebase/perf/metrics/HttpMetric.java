// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.perf.metrics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.perf.FirebasePerformance.HttpMethod;
import com.google.firebase.perf.FirebasePerformanceAttributable;
import com.google.firebase.perf.config.ConfigResolver;
import com.google.firebase.perf.impl.NetworkRequestMetricBuilder;
import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.validator.PerfMetricValidator;
import java.net.URL;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Metric used to collect data for network requests/responses. A new object must be used for every
 * request/response. This class is not thread safe.
 */
public class HttpMetric implements FirebasePerformanceAttributable {

  private static final AndroidLogger logger = AndroidLogger.getInstance();

  private NetworkRequestMetricBuilder mMetricBuilder;
  private Timer mTimer;
  private final Map<String, String> mAttributes;
  private boolean isStopped = false;
  private boolean isDisabled = false;

  /**
   * Constructs a new NetworkRequestMetricBuilder object given a String url value
   *
   * @hide
   */
  /** @hide */
  public HttpMetric(
      String url, @HttpMethod String httpMethod, TransportManager transportManager, Timer timer) {
    mAttributes = new ConcurrentHashMap<>();
    mTimer = timer;

    mMetricBuilder =
        NetworkRequestMetricBuilder.builder(transportManager).setUrl(url).setHttpMethod(httpMethod);
    mMetricBuilder.setManualNetworkRequestMetric();

    if (!ConfigResolver.getInstance().isPerformanceMonitoringEnabled()) {
      logger.info("HttpMetric feature is disabled. URL %s", url);
      isDisabled = true;
    }
  }

  /**
   * Constructs a new NetworkRequestMetricBuilder object given a URL value
   *
   * @hide
   */
  /** @hide */
  public HttpMetric(
      URL url, @HttpMethod String httpMethod, TransportManager transportManager, Timer timer) {
    this(url.toString(), httpMethod, transportManager, timer);
  }

  /**
   * Sets the httpResponse code of the request
   *
   * @param responseCode valid values are greater than 0. Invalid usage will be logged.
   */
  public void setHttpResponseCode(int responseCode) {
    mMetricBuilder.setHttpResponseCode(responseCode);
  }

  /**
   * Sets the size of the request payload
   *
   * @param bytes valid values are greater than or equal to 0. Invalid usage will be logged.
   */
  public void setRequestPayloadSize(long bytes) {
    mMetricBuilder.setRequestPayloadBytes(bytes);
  }

  /**
   * Sets the size of the response payload
   *
   * @param bytes valid values are greater than or equal to 0. Invalid usage will be logged.
   */
  public void setResponsePayloadSize(long bytes) {
    mMetricBuilder.setResponsePayloadBytes(bytes);
  }

  /**
   * Content type of the response such as text/html, application/json, etc...
   *
   * @param contentType valid string of MIME type. Invalid usage will be logged.
   */
  public void setResponseContentType(@Nullable String contentType) {
    mMetricBuilder.setResponseContentType(contentType);
  }

  /** Marks the start time of the request */
  public void start() {
    mTimer.reset();
    mMetricBuilder.setRequestStartTimeMicros(mTimer.getMicros());
  }

  /**
   * Marks the end time of the request
   *
   * @hide
   */
  /** @hide */
  public void markRequestComplete() {
    mMetricBuilder.setTimeToRequestCompletedMicros(mTimer.getDurationMicros());
  }

  /**
   * Marks the start time of the response
   *
   * @hide
   */
  /** @hide */
  public void markResponseStart() {
    mMetricBuilder.setTimeToResponseInitiatedMicros(mTimer.getDurationMicros());
  }

  /**
   * Marks the end time of the response and queues the network request metric on the device for
   * transmission. Check logcat for transmission info.
   */
  public void stop() {
    if (isDisabled) {
      return;
    }

    mMetricBuilder
        .setTimeToResponseCompletedMicros(mTimer.getDurationMicros())
        .setCustomAttributes(mAttributes)
        .build();
    isStopped = true;
  }

  /**
   * Sets a String value for the specified attribute. Updates the value of the attribute if the
   * attribute already exists. If the HttpMetric has been stopped, this method returns without
   * adding the attribute. The maximum number of attributes that can be added to a HttpMetric are
   * {@link #MAX_TRACE_CUSTOM_ATTRIBUTES}.
   *
   * @param attribute name of the attribute
   * @param value value of the attribute
   */
  @Override
  public void putAttribute(@NonNull String attribute, @NonNull String value) {
    boolean noError = true;
    try {
      attribute = attribute.trim();
      value = value.trim();
      checkAttribute(attribute, value);
      logger.debug(
          "Setting attribute '%s' to %s on network request '%s'",
          attribute, value, mMetricBuilder.getUrl());
    } catch (Exception e) {
      logger.error(
          "Cannot set attribute '%s' with value '%s' (%s)", attribute, value, e.getMessage());
      noError = false;
    }
    if (noError) {
      mAttributes.put(attribute, value);
    }
  }

  private void checkAttribute(@Nullable String attribute, @Nullable String value) {
    if (isStopped) {
      throw new IllegalArgumentException(
          "HttpMetric has been logged already so unable to modify attributes");
    }
    if (attribute == null || value == null) {
      throw new IllegalArgumentException("Attribute must not have null key or value.");
    }
    if (!mAttributes.containsKey(attribute)
        && mAttributes.size() >= Constants.MAX_TRACE_CUSTOM_ATTRIBUTES) {
      throw new IllegalArgumentException(
          String.format(
              Locale.ENGLISH,
              "Exceeds max limit of number of attributes - %d",
              Constants.MAX_TRACE_CUSTOM_ATTRIBUTES));
    }
    String err =
        PerfMetricValidator.validateAttribute(new AbstractMap.SimpleEntry<>(attribute, value));
    if (err != null) {
      throw new IllegalArgumentException(err);
    }
  }

  /**
   * Removes an already added attribute from the HttpMetric. If the HttpMetric has already been
   * stopped, this method returns without removing the attribute.
   *
   * @param attribute name of the attribute to be removed from the running Traces.
   */
  @Override
  public void removeAttribute(@NonNull String attribute) {
    if (isStopped) {
      logger.error("Can't remove a attribute from a HttpMetric that's stopped.");
      return;
    }
    mAttributes.remove(attribute);
  }

  /**
   * Returns the value of an attribute.
   *
   * @param attribute name of the attribute to fetch the value for
   * @return The value of the attribute if it exists or null otherwise.
   */
  @Override
  @Nullable
  public String getAttribute(@NonNull String attribute) {
    return mAttributes.get(attribute);
  }

  /**
   * Returns the map of all the attributes added to this HttpMetric.
   *
   * @return map of attributes and its values currently added to this HttpMetric
   */
  @Override
  @NonNull
  public Map<String, String> getAttributes() {
    return new HashMap<>(mAttributes);
  }
}
