/**
 * Copyright 2015 Confluent Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/

package com.hortonworks.kafkarest.unit;

import com.hortonworks.kafkarest.Context;
import com.hortonworks.kafkarest.Errors;
import com.hortonworks.kafkarest.MetadataObserver;
import com.hortonworks.kafkarest.RecordMetadataOrException;
import com.hortonworks.kafkarest.entities.EmbeddedFormat;
import com.hortonworks.kafkarest.entities.PartitionOffset;
import com.hortonworks.kafkarest.entities.PartitionProduceRequest;
import com.hortonworks.kafkarest.entities.ProduceRecord;
import com.hortonworks.kafkarest.entities.ProduceResponse;
import com.hortonworks.kafkarest.resources.TopicsResource;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import com.hortonworks.registries.schemaregistry.SchemaIdVersion;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;

import com.hortonworks.kafkarest.KafkaRestApplication;
import com.hortonworks.kafkarest.KafkaRestConfig;
import com.hortonworks.kafkarest.ProducerPool;
import com.hortonworks.kafkarest.TestUtils;
import com.hortonworks.kafkarest.entities.AvroProduceRecord;
import com.hortonworks.kafkarest.entities.SchemaHolder;
import com.hortonworks.kafkarest.resources.PartitionsResource;
import io.confluent.rest.EmbeddedServerTestHarness;
import io.confluent.rest.RestConfigException;
import io.confluent.rest.exceptions.ConstraintViolationExceptionMapper;

import static com.hortonworks.kafkarest.TestUtils.assertErrorResponse;
import static com.hortonworks.kafkarest.TestUtils.assertOKResponse;
import static org.junit.Assert.assertEquals;

// This test is much lighter than the Binary one since they would otherwise be mostly duplicated
// -- this just sanity checks the Jersey processing of these requests.
public class PartitionsResourceAvroProduceTest
    extends EmbeddedServerTestHarness<KafkaRestConfig, KafkaRestApplication> {

  private static final SchemaIdVersion KEY_SCHEMA_ID = new SchemaIdVersion(1L, 1);
  private static final SchemaIdVersion VALUE_SCHEMA_ID = new SchemaIdVersion(2L, 2);
  private MetadataObserver mdObserver;
  private ProducerPool producerPool;
  private Context ctx;

  private final String topicName = "topic1";

  private List<AvroProduceRecord> produceRecordsWithKeys;
  private List<RecordMetadataOrException> produceResults;
  private final List<PartitionOffset> offsetResults;

  // This test assumes that AvroConverterTest is good enough and testing one primitive type for
  // keys and one complex type for records is sufficient.
  private static final String keySchemaStr = "{\"name\":\"int\",\"type\": \"int\"}";
  private static final String valueSchemaStr = "{\"type\": \"record\", "
                                               + "\"name\":\"test\","
                                               + "\"fields\":[{"
                                               + "  \"name\":\"field\", "
                                               + "  \"type\": \"int\""
                                               + "}]}";

  public PartitionsResourceAvroProduceTest() throws RestConfigException {
    mdObserver = EasyMock.createMock(MetadataObserver.class);
    producerPool = EasyMock.createMock(ProducerPool.class);
    ctx = new Context(config, mdObserver, producerPool, null, null);

    addResource(new TopicsResource(ctx));
    addResource(new PartitionsResource(ctx));

    produceRecordsWithKeys = Arrays.asList(
        new AvroProduceRecord(TestUtils.jsonTree("1"), TestUtils.jsonTree("{\"field\":42}")),
        new AvroProduceRecord(TestUtils.jsonTree("2"), TestUtils.jsonTree("{\"field\":84}"))
    );
    TopicPartition tp0 = new TopicPartition(topicName, 0);
    produceResults = Arrays.asList(
        new RecordMetadataOrException(new RecordMetadata(tp0, 0, 0, 0, 0, 1, 1), null),
        new RecordMetadataOrException(new RecordMetadata(tp0, 0, 1, 0, 0, 1, 1), null)
    );
    offsetResults = Arrays.asList(
        new PartitionOffset(0, 0L, null, null),
        new PartitionOffset(0, 1L, null, null)
    );
  }

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
    EasyMock.reset(mdObserver, producerPool);
  }

  private <K, V> Response produceToPartition(String topic, int partition,
                                             PartitionProduceRequest request,
                                             String acceptHeader,
                                             String requestMediatype,
                                             EmbeddedFormat recordFormat,
                                             final List<RecordMetadataOrException> results) {
    final Capture<ProducerPool.ProduceRequestCallback>
        produceCallback =
        new Capture<ProducerPool.ProduceRequestCallback>();
    EasyMock.expect(mdObserver.topicExists(topic)).andReturn(true);
    EasyMock.expect(mdObserver.partitionExists(topic, partition)).andReturn(true);
    producerPool.produce(EasyMock.eq(topic),
                         EasyMock.eq(partition),
                         EasyMock.eq(recordFormat),
                         EasyMock.<SchemaHolder>anyObject(),
                         EasyMock.<Collection<? extends ProduceRecord<K, V>>>anyObject(),
                         EasyMock.capture(produceCallback));
    EasyMock.expectLastCall().andAnswer(new IAnswer<Object>() {
      @Override
      public Object answer() throws Throwable {
        if (results == null) {
          throw new Exception();
        } else {
          produceCallback.getValue().onCompletion(KEY_SCHEMA_ID, VALUE_SCHEMA_ID, results);
        }
        return null;
      }
    });
    EasyMock.replay(mdObserver, producerPool);

    Response
        response =
        request("/topics/" + topic + "/partitions/" + ((Integer) partition).toString(),
                acceptHeader)
            .post(Entity.entity(request, requestMediatype));

    EasyMock.verify(mdObserver, producerPool);

    return response;
  }

  @Test
  public void testProduceToPartitionByKey() {
    for (TestUtils.RequestMediaType mediatype : TestUtils.V1_ACCEPT_MEDIATYPES) {
      for (String requestMediatype : TestUtils.V1_REQUEST_ENTITY_TYPES_AVRO) {
        final PartitionProduceRequest request = new PartitionProduceRequest();
        request.setRecords(produceRecordsWithKeys);
        request.setKeySchema(keySchemaStr);
        request.setValueSchema(valueSchemaStr);
        Response rawResponse =
            produceToPartition(topicName, 0, request, mediatype.header, requestMediatype,
                               EmbeddedFormat.AVRO, produceResults);
        assertOKResponse(rawResponse, mediatype.expected);
        ProduceResponse response = rawResponse.readEntity(ProduceResponse.class);

        assertEquals(offsetResults, response.getOffsets());
        assertEquals(KEY_SCHEMA_ID, response.getKeySchemaId());
        assertEquals(VALUE_SCHEMA_ID, response.getValueSchemaId());

        EasyMock.reset(mdObserver, producerPool);
      }
    }

    // Now use schema IDs
    for (TestUtils.RequestMediaType mediatype : TestUtils.V1_ACCEPT_MEDIATYPES) {
      for (String requestMediatype : TestUtils.V1_REQUEST_ENTITY_TYPES_AVRO) {
        final PartitionProduceRequest request = new PartitionProduceRequest();
        request.setRecords(produceRecordsWithKeys);
        request.setKeySchemaId(KEY_SCHEMA_ID);
        request.setValueSchemaId(VALUE_SCHEMA_ID);
        Response rawResponse =
            produceToPartition(topicName, 0, request, mediatype.header, requestMediatype,
                               EmbeddedFormat.AVRO, produceResults);
        assertOKResponse(rawResponse, mediatype.expected);
        ProduceResponse response = rawResponse.readEntity(ProduceResponse.class);

        assertEquals(offsetResults, response.getOffsets());
        assertEquals(KEY_SCHEMA_ID, response.getKeySchemaId());
        assertEquals(VALUE_SCHEMA_ID, response.getValueSchemaId());

        EasyMock.reset(mdObserver, producerPool);
      }
    }
  }

  @Test
  public void testProduceMissingSchema() {
    for (TestUtils.RequestMediaType mediatype : TestUtils.V1_ACCEPT_MEDIATYPES) {
      for (String requestMediatype : TestUtils.V1_REQUEST_ENTITY_TYPES_AVRO) {
        final PartitionProduceRequest request = new PartitionProduceRequest();
        request.setRecords(produceRecordsWithKeys);
        Response rawResponse =
            request("/topics/" + topicName + "/partitions/0", mediatype.header)
                .post(Entity.entity(request, requestMediatype));

        assertErrorResponse(ConstraintViolationExceptionMapper.UNPROCESSABLE_ENTITY,
                            rawResponse,
                            Errors.KEY_SCHEMA_MISSING_ERROR_CODE,
                            null,
                            mediatype.expected);

        EasyMock.reset(mdObserver, producerPool);
      }
    }
  }

}
