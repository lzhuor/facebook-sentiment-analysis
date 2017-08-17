package lzr.io.sentiment.verticle;

import com.semantria.CallbackHandler;
import com.semantria.Session;
import com.semantria.interfaces.ISerializer;
import com.semantria.mapping.Document;
import com.semantria.mapping.output.*;
import com.semantria.serializer.JsonSerializer;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;
import lzr.io.sentiment.service.DataCollectionService;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

/**
 * Created by lzr.io on 1/11/2015.
 */
public class WorkerVerticle extends AbstractVerticle {
    private static final int TIMEOUT_BEFORE_GETTING_RESPONSE = 500; //in millisec
    private Logger vertxLogger;
    @Override
    public void start() throws Exception {
        // Get Vertx Logger
        vertxLogger = LoggerFactory.getLogger(WorkerVerticle.class);

        // Set mongoDb Config
        JsonObject mongodbConfig = new JsonObject()
                .put(ServerAttribute.MONGODB_SERVER_CONN_URL_KEY, config().getString(ServerAttribute.MONGODB_SERVER_CONN_CONF_JSON_KEY))
                .put(ServerAttribute.MONGODB_DB_NAME_KEY, config().getString(ServerAttribute.MONGODB_DB_NAME_CONF_JSON_KEY));

        MongoClient mongoClient = MongoClient.createShared(vertx, mongodbConfig);

        DataCollectionService dataCollectionService = new DataCollectionService(vertx, mongodbConfig);

        System.out.println("[Worker] Starting in " + Thread.currentThread().getName());

        vertx.eventBus().consumer("sentiment.analysis", message -> {
            try {
                System.out.println("[Worker] Consuming data in " + Thread.currentThread().getName());
                String userId = (String) message.body();
                // Use correct Semantria API credentias here
                String key = "*"; // TODO: Use your own key
                String secret = "*"; // TODO: Use your own secret

                Hashtable<String, TaskStatus> docsTracker = new Hashtable<String, TaskStatus>();
                List<JsonObject> initialPosts = new ArrayList<JsonObject>();

                JsonObject query = new JsonObject();

                mongoClient.find(userId, query, res -> {
                    if (res.succeeded()) {
                        // set page status to analyzing
                        dataCollectionService.setPageStatusAnalyzing(userId);

                        for (JsonObject json : res.result()) {
                            for (Object comment : json.getJsonArray("comments")) {
                                JsonObject commentObject = (JsonObject) comment;
                                initialPosts.add(commentObject);
                            }
                        }

                        // Creates JSON serializer instance
                        //ISerializer jsonSerializer = new JsonSerializer();
                        // Initializes new session with the serializer object and the keys.
                        ISerializer serializer = new JsonSerializer();
                        Session session = Session.createSession(key, secret, serializer, true);
                        session.setCallbackHandler(new CallbackHandler());

                        //Obtaining subscription object to get user limits applicable on server side
                        Subscription subscription = session.getSubscription();

                        List<Document> outgoingBatch = new ArrayList<Document>(subscription.getBasicSettings().getBatchLimit());

                        for (JsonObject comment : initialPosts) {

                            if (!comment.containsKey("sentiment")) {
                                String commentMessage = comment.getString("message");

                                if (commentMessage.length() > 5000) {
                                    commentMessage = commentMessage.substring(0, 5000);
                                }

                                Document doc = new Document(comment.getString("id"), commentMessage);outgoingBatch.add(doc);
                                docsTracker.put(comment.getString("id"), TaskStatus.QUEUED);

                                if (outgoingBatch.size() == subscription.getBasicSettings().getBatchLimit()) {
                                    if (session.queueBatch(outgoingBatch) == 202) {
                                        vertxLogger.info("\"" + outgoingBatch.size() + "\" documents queued successfully.");
                                        outgoingBatch.clear();
                                    }
                                }
                            }
                        }

                        if (outgoingBatch.size() > 0) {
                            if (session.queueBatch(outgoingBatch) == 202) {
                                vertxLogger.info("\"" + outgoingBatch.size() + "\" documents queued successfully.");
                                outgoingBatch.clear();
                            }
                        }

                        System.out.println();
                        try {
                            List<DocAnalyticData> processed = new ArrayList<DocAnalyticData>();

                            int counter = 0;

                            while (docsTracker.containsValue(TaskStatus.QUEUED) && counter < 1000) {
                                // As Semantria sentiment API isn't real-time solution you need to wait some time before getting of the processed results
                                // In real application here can be implemented two separate jobs, one for queuing of source data another one for retrieving
                                // Wait half of second while Semantria process queued document

                                Thread.currentThread().sleep(TIMEOUT_BEFORE_GETTING_RESPONSE);

                                counter++;

                                // Requests processed results from Semantria service
                                List<DocAnalyticData> temp = session.getProcessedDocuments();
                                for (Iterator<DocAnalyticData> i = temp.iterator(); i.hasNext(); ) {
                                    DocAnalyticData item = i.next();

                                    if (docsTracker.containsKey(item.getId())) {
                                        processed.add(item);
                                        docsTracker.put(item.getId(), TaskStatus.PROCESSED);
                                    }
                                }
                                System.out.println("Retrieving your processed results...");
                            }

                            // Output results
                            JsonArray sentimentResultArray = new JsonArray();

                            for (DocAnalyticData doc : processed) {
                                // Create sentiment JsonObject
                                JsonObject sentiment = new JsonObject();
                                sentiment.put("job_id", doc.getId());
                                sentiment.put("sentiment_score", Float.toString(doc.getSentimentScore()));
                                sentiment.put("sentiment_polarity", doc.getSentimentPolarity());

                                System.out.println("Document:\n\tid: " + doc.getId() + "\n\tsentiment score: " + Float.toString(doc.getSentimentScore()) + "\n\tsentiment polarity: " + doc.getSentimentPolarity());
                                System.out.println();
                                if (doc.getAutoCategories() != null) {
                                    System.out.println("\tdocument categories:");

                                    JsonArray categoryArray = new JsonArray();
                                    for (DocCategory category : doc.getAutoCategories()) {
                                        System.out.println("\t\ttopic: " + category.getTitle() + " \n\t\tStrength score: " + Float.toString(category.getStrengthScore()));
                                        System.out.println();

                                        // Add each category into category array
                                        categoryArray.add(new JsonObject().put("topic", category.getTitle()).put("strength_score", Float.toString(category.getStrengthScore())));
                                    }

                                    // Add categories into sentiment
                                   sentiment.put("category", categoryArray);
                                }
                                if (doc.getThemes() != null) {
                                    System.out.println("\tdocument themes:");

                                    JsonArray themeArray = new JsonArray();

                                    for (DocTheme theme : doc.getThemes()) {
                                        System.out.println("\t\ttitle: " + theme.getTitle() + " \n\t\tsentiment: " + Float.toString(theme.getSentimentScore()) + "\n\t\tsentiment polarity: " + theme.getSentimentPolarity());
                                        System.out.println();

                                        // Add each theme into theme array
                                        themeArray.add(new JsonObject().put("theme", theme.getTitle()).put("sentiment", Float.toString(theme.getSentimentScore())));
                                    }

                                    // Add themes into sentiment
                                    sentiment.put("theme", themeArray);
                                }
                                if (doc.getEntities() != null) {
                                    System.out.println("\tentities:");

                                    JsonArray entityArray = new JsonArray();

                                    for (DocEntity entity : doc.getEntities()) {
                                        System.out.println("\t\ttitle: " + entity.getTitle() + "\n\t\tsentiment: " + Float.toString(entity.getSentimentScore()) + "\n\t\tsentiment polarity: " + entity.getSentimentPolarity());

                                        // Add each entity into entity array
                                        entityArray.add(new JsonObject().put("entity", entity.getTitle()).put("sentiment", Float.toString(entity.getSentimentScore())));
                                    }

                                    // Add entities into sentiment
                                    sentiment.put("entity", entityArray);
                                }
                                sentimentResultArray.add(sentiment);
                            }

                            // Insert sentiment result into each post's comment array
                            mongoClient.find(userId, query, findPostsResult -> {
                                if (res.succeeded() && findPostsResult.succeeded()) {
                                    // for loop each post
                                    for (JsonObject json : res.result()) {
                                        JsonArray processedCommentWithSentiment = new JsonArray();
                                        // for loop each comment of the post
                                        for (Object comment : json.getJsonArray("comments")) {
                                            JsonObject commentObject = (JsonObject) comment;
                                            String commentId = commentObject.getString("id");
                                            // for loop sentiment result of each comment in the result set
                                            boolean isSentimentResultOfTheCommentFound = false;
                                            for(Object sentimentObject : sentimentResultArray){
                                                JsonObject sentimentJsonObject = (JsonObject)sentimentObject;
                                                if(commentId.contains(sentimentJsonObject.getString("job_id"))){
                                                    commentObject.put("sentiment", sentimentJsonObject);
                                                    processedCommentWithSentiment.add(commentObject);
                                                    isSentimentResultOfTheCommentFound = true;
                                                }
                                            }

                                            if (isSentimentResultOfTheCommentFound == false){
                                                processedCommentWithSentiment.add(commentObject);
                                            }
                                        }


                                        // Update comment array in mongodb
                                        dataCollectionService.updateCommentField(userId, json.getString("id"), processedCommentWithSentiment);
                                    }

                                    // set page status to "ready for viewing the sentiment result"
                                    dataCollectionService.setPageStatusReadyForViewingResult(userId);
                                }
                            });

                        } catch (Exception e) {
                            vertxLogger.error(e.getMessage());

                            // set page status to "ready for analyzing"
                            dataCollectionService.setPageStatusReadyForAnalyzing(userId);
                        }
                    } else {
                        res.cause().printStackTrace();
                    }
                });
                message.reply(userId + " is under sentiment analysis. It will take a sec, you can go and have a coffee");

            } catch (Exception e) {
                e.printStackTrace();
                message.reply(e.getMessage());
            }
        });
    }
}
