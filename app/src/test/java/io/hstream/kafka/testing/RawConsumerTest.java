package io.hstream.kafka.testing;

import io.hstream.kafka.testing.Utils.RawConsumerBuilder;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import static io.hstream.kafka.testing.Utils.Common.*;

@ExtendWith(ClusterExtension.class)
public class RawConsumerTest {
  private static final Logger logger = LoggerFactory.getLogger(RawConsumerTest.class);
  private String HStreamUrl = "127.0.0.1:9092";
  private AdminClient client;

  public void setHStreamUrl(String url) {
    this.HStreamUrl = url;
  }

  @BeforeEach
  void setUp() {
    Properties adminProps = new Properties();
    adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, HStreamUrl);
    try {
      client = AdminClient.create(adminProps);
    } catch (Exception e) {
      logger.error("create admin client failed: {}", e.toString());
      throw e;
    }
  }

  @AfterEach
  void tearDown() {
    client.close();
  }

  @Test
  @Timeout(40)
  void testMultiConsumerWithMultiPartitions() {
    var topic = randomTopicName("abc");
    var partitions = 3;
    createTopic(client, topic, partitions, (short) 1);
    var producer = createByteProducer(HStreamUrl);
    for (int i = 0; i < partitions; i++) {
      sendBytesRecords(producer, 10, new TopicPartition(topic, i));
    }

    var consumers = new LinkedList<Consumer<byte[], byte[]>>();
    for (int i = 0; i < partitions; i++) {
      var consumer = new RawConsumerBuilder<byte[], byte[]>(HStreamUrl).build();
      var tp = new TopicPartition(topic, i);
      consumer.assign(List.of(tp));
      consumer.seek(tp, 0);
      consumers.add(consumer);
    }
    var result = pollConcurrently(consumers, 10000);
    for (int i = 0; i < partitions; i++) {
      Assertions.assertTrue(result.containsKey(new TopicPartition(topic, i)));
      Assertions.assertEquals(10, result.get(new TopicPartition(topic, i)).size());
      // TODO: check result data
    }
  }
}