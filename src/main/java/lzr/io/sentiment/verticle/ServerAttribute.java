package lzr.io.sentiment.verticle;

/**
 * Created by lzr.io on 12/10/15.
 */
public interface ServerAttribute {
    // Databases
    String MONGODB_SERVER_CONN_URL_KEY = "connection_string";
    String MONGODB_SERVER_CONN_CONF_JSON_KEY = "mongodb.server.connection.url";
    String MONGODB_DB_NAME_KEY = "db_name";
    String MONGODB_DB_NAME_CONF_JSON_KEY = "mongodb.sentiment.db";

    // Ports
    int SERVER_DEFAULT_PORT = 8080;
    String SERVER_PORT_CONF_KEY = "http.port";

    // Timeout Config
    int HTTP_SERVICE_DEFAULT_TIMEOUT = 10000;
    String HTTP_SERVICE_TIMEOUT_KEY = "http.timeout";

    // Facebook API
    int HTTPS_FACEBOOK_CONN_PORT = 443;
    String FACEBOOK_GRAPH_API_HOST = "facebook.graph.API.host";
    String FACEBOOK_GRAPH_API_TOKEN = "facebook.graph.API.token";


    // API Routes

    // Response Attributes

}
