package org.mongodb.hadoop;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.hadoop.examples.treasury.TreasuryYieldXMLConfig;
import com.mongodb.hadoop.splitter.MultiMongoCollectionSplitter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assume.assumeFalse;

public class TestStandalone extends BaseHadoopTest {
    private static final Log LOG = LogFactory.getLog(TestStandalone.class);

    @Test
    public void testBasicInputSource() {
        assumeFalse(isSharded());
        LOG.info("testing basic input source");
        new MapReduceJob(TreasuryYieldXMLConfig.class)
            .param("mongo.input.notimeout", "true")
            .execute(inVM);
        compareResults(getClient().getDB("mongo_hadoop").getCollection("yield_historical.out"), getReference());
    }

    @Test
    public void testTreasuryJsonConfig() {
        assumeFalse(isSharded());
        mongoImport("yield_historical.in3", JSONFILE_PATH);
        new MapReduceJob(TreasuryYieldXMLConfig.class)
            .param("mongo.splitter.class", MultiMongoCollectionSplitter.class.getName())
            .param("mongo.input.multi_uri.json", "\"" + collectionSettings().toString().replace("\"", "\\\"") + '"')
            .execute(inVM);

        DBCollection out = getClient().getDB("mongo_hadoop").getCollection("yield_historical.out");
        compareResults(out, getReference());
    }

    @Test
    public void testMultipleCollectionSupport() {
        assumeFalse(isSharded());
        mongoImport("yield_historical.in", JSONFILE_PATH);
        mongoImport("yield_historical.in2", JSONFILE_PATH);
        new MapReduceJob(TreasuryYieldXMLConfig.class)
            .param("mongo.splitter.class", MultiMongoCollectionSplitter.class.getName())
            .inputCollections("mongo_hadoop.yield_historical.in", "mongo_hadoop.yield_historical.in2")
            .execute(inVM);

        DBCollection out = getClient().getDB("mongo_hadoop").getCollection("yield_historical.out");
        List<DBObject> referenceDoubled = new ArrayList<DBObject>();
        for (DBObject object : getReference()) {
            DBObject doubled = new BasicDBObject();
            doubled.putAll(object);
            referenceDoubled.add(doubled);
            Integer count = ((Integer) object.get("count")) * 2;
            Double sum = ((Double) object.get("sum")) * 2;

            doubled.put("count", count);
            doubled.put("avg", sum / count);
            doubled.put("sum", sum);
        }

        compareResults(out, referenceDoubled);
    }

    private ArrayNode collectionSettings() {
        ArrayNode settings = new ArrayNode(JsonNodeFactory.instance);
        ObjectNode node = new ObjectNode(JsonNodeFactory.instance);
        node.put("mongo.input.uri", "mongodb://localhost/mongo_hadoop.yield_historical.in");
        ObjectNode dow = new ObjectNode(JsonNodeFactory.instance);
        dow.put("dayOfWeek", "FRIDAY");
        node.put("query", dow);
        node.put("mongo.splitter.class", "com.mongodb.hadoop.splitter.SingleMongoSplitter");
        node.put("mongo.input.split.use_range_queries", true);
        node.put("mongo.input.notimeout", true);
        settings.add(node);

        node = new ObjectNode(JsonNodeFactory.instance);
        node.put("mongo.input.uri", "mongodb://localhost/mongo_hadoop.yield_historical.in3");
        node.put("mongo.input.split.use_range_queries", true);
        node.put("mongo.input.notimeout", true);
        settings.add(node);
        return settings;
    }
}
