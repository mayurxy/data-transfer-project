/*
 * Copyright 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dataportabilityproject.serviceProviders.fiveHundredPx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import org.dataportabilityproject.cloud.interfaces.JobDataCache;
import org.dataportabilityproject.dataModels.ExportInformation;
import org.dataportabilityproject.dataModels.Exporter;
import org.dataportabilityproject.dataModels.Importer;
import org.dataportabilityproject.dataModels.photos.PhotoAlbum;
import org.dataportabilityproject.dataModels.photos.PhotoModel;
import org.dataportabilityproject.dataModels.photos.PhotosModelWrapper;
import org.dataportabilityproject.serviceProviders.fiveHundredPx.model.FiveHundredPxResponse;

// TODO(olsona): address image sizing (1,2,3,...)
// TODO(olsona): what is the 500px equivalent of "/api/v2!authuser"?  Is there one?
// TODO(olsona): write custom mapper to address TRUE/FALSE coming up in JSON response
// TODO(olsona): address license types (for now, assume no problem)

final public class FiveHundredPxPhotoService implements Exporter<PhotosModelWrapper>,
    Importer<PhotosModelWrapper> {

  private static final ObjectMapper MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private static final String BASE_URL = "https://api.500px.com";

  private final OAuthConsumer authConsumer;
  private final HttpTransport httpTransport;
  private final JobDataCache jobDataCache;

  FiveHundredPxPhotoService(OAuthConsumer authConsumer, JobDataCache jobDataCache)
      throws IOException {
    this.jobDataCache = jobDataCache;
    try {
      this.httpTransport = GoogleNetHttpTransport.newTrustedTransport();
      this.authConsumer = authConsumer;
    } catch (GeneralSecurityException e) {
      throw new IllegalArgumentException("Couldn't create 500px API", e);
    }
  }

  @Override
  public PhotosModelWrapper export(ExportInformation continuationInformation) throws IOException {
    return null;
  }

  @Override
  public void importItem(PhotosModelWrapper wrapper) throws IOException {
    HashMap<Integer, Integer> galleriesToPhotosMap = new HashMap<>();
    // Photos are uploaded first.
    for (PhotoModel photo : wrapper.getPhotos()) {
      // Upload photo
      // Get photo ID from upload
      // Add <albumId, photoId> to map
    }
    if (!wrapper.getAlbums().isEmpty()) {
      for (PhotoAlbum album : wrapper.getAlbums()) {
        // Make gallery
        // Tell gallery which photos belong to it using map
      }
    }
  }

  // This could also be pulled out into a library.
  private <T> FiveHundredPxResponse<T> makeRequest(String url,
      TypeReference<FiveHundredPxResponse<T>> typeReference) throws IOException {
    HttpRequestFactory requestFactory = httpTransport.createRequestFactory();
    String signedRequest;
    try {
      signedRequest = this.authConsumer.sign(BASE_URL + url + "?_accept=application%2Fjson");
    } catch (OAuthMessageSignerException
        | OAuthExpectationFailedException
        | OAuthCommunicationException e) {
      throw new IOException("Couldn't make request", e);
    }
    HttpRequest getRequest = requestFactory.buildGetRequest(new GenericUrl(signedRequest));
    HttpResponse response = getRequest.execute();
    int statusCode = response.getStatusCode();
    if (statusCode != 200) {
      throw new IOException(
          String.format("Bad status code: %d error: %s", statusCode, response.getStatusMessage()));
    }
    String result = CharStreams
        .toString(new InputStreamReader(response.getContent(), Charsets.UTF_8));
    return MAPPER.readValue(result, typeReference);
  }

  private <T> FiveHundredPxResponse<T> postRequest(String url, HttpContent content,
      Map<String, String> headers, TypeReference<T> typeReference) throws IOException {
    HttpRequestFactory requestFactory = httpTransport.createRequestFactory();

    String fullUrl = url;
    if (!fullUrl.contains("://")) {
      fullUrl = BASE_URL + fullUrl;
    }

    HttpRequest postRequest = requestFactory.buildPostRequest(new GenericUrl(fullUrl), content);
    HttpHeaders httpHeaders = new HttpHeaders().setAccept("application/json")
        .setContentType("application/json");
    for (Entry<String, String> entry : headers.entrySet()) {
      httpHeaders.put(entry.getKey(), entry.getValue());
    }
    postRequest.setHeaders(httpHeaders);

    try {
      postRequest = (HttpRequest) this.authConsumer.sign(postRequest).unwrap();
    } catch (OAuthMessageSignerException
        | OAuthExpectationFailedException
        | OAuthCommunicationException e) {
      throw new IOException("Couldn't create post request", e);
    }

    HttpResponse response;
    try {
      response = postRequest.execute();
    } catch (HttpResponseException e) {
      throw new IOException("Problem making request: " + postRequest.getUrl(), e);
    }
    int statusCode = response.getStatusCode();
    if (statusCode < 200 || statusCode >= 300) {
      throw new IOException(
          String.format("Bad status code: %d error: %s", statusCode, response.getStatusMessage()));
    }
    String result = CharStreams.toString(new InputStreamReader(
        response.getContent(), Charsets.UTF_8));

    return MAPPER.readValue(result, typeReference);
  }

  // Could this be made available to all services?  Seems a broadly useful method.
  private static InputStream getImageAsStream(String urlStr) throws IOException {
    URL url = new URL(urlStr);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.connect();
    return conn.getInputStream();
  }
}
