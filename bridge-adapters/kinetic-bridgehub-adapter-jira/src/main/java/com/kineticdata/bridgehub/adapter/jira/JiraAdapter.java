package com.kineticdata.bridgehub.adapter.jira;

import com.kineticdata.bridgehub.adapter.BridgeAdapter;
import com.kineticdata.bridgehub.adapter.BridgeError;
import com.kineticdata.bridgehub.adapter.BridgeRequest;
import com.kineticdata.bridgehub.adapter.BridgeUtils;
import com.kineticdata.bridgehub.adapter.Count;
import com.kineticdata.bridgehub.adapter.Record;
import com.kineticdata.bridgehub.adapter.RecordList;
import com.kineticdata.commons.v1.config.ConfigurableProperty;
import com.kineticdata.commons.v1.config.ConfigurablePropertyMap;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.CompareToBuilder;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class JiraAdapter implements BridgeAdapter {
    /*----------------------------------------------------------------------------------------------
     * PROPERTIES
     *--------------------------------------------------------------------------------------------*/

    /** Defines the adapter display name. */
    public static final String NAME = "Jira Bridge";

    /** Defines the logger */
    protected static final org.slf4j.Logger logger = LoggerFactory.getLogger(JiraAdapter.class);

    /** Adapter version constant. */
    public static String VERSION;
    /** Load the properties version from the version.properties file. */
    static {
        try {
            java.util.Properties properties = new java.util.Properties();
            properties.load(JiraAdapter.class.getResourceAsStream("/"+JiraAdapter.class.getName()+".version"));
            VERSION = properties.getProperty("version");
        } catch (IOException e) {
            logger.warn("Unable to load "+JiraAdapter.class.getName()+" version properties.", e);
            VERSION = "Unknown";
        }
    }

    /** Defines the collection of property names for the adapter. */
    public static class Properties {
        public static final String USERNAME = "Username";
        public static final String PASSWORD = "Password";
        public static final String BASESITE = "Base Site";
    }

    private String username;
    private String password;
    private String baseSite;

    private final ConfigurablePropertyMap properties = new ConfigurablePropertyMap(
            new ConfigurableProperty(Properties.USERNAME).setIsRequired(true),
            new ConfigurableProperty(Properties.PASSWORD).setIsRequired(true).setIsSensitive(true),
            new ConfigurableProperty(Properties.BASESITE).setIsRequired(true)
    );

    /**
     * Structures that are valid to use in the bridge
     */
    public static final List<String> VALID_STRUCTURES = Arrays.asList(new String[] {
        "User","Group","Issue","Project"
    });

    /*---------------------------------------------------------------------------------------------
     * SETUP METHODS
     *-------------------------------------------------------------------------------------------*/
    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getVersion() {
       return VERSION;
    }

    @Override
    public ConfigurablePropertyMap getProperties() {
        return properties;
    }

    @Override
    public void setProperties(Map<String,String> parameters) {
        properties.setValues(parameters);
    }

    @Override
    public void initialize() throws BridgeError {
        this.baseSite = properties.getValue(Properties.BASESITE);
        this.username = properties.getValue(Properties.USERNAME);
        this.password = properties.getValue(Properties.PASSWORD);

        // Testing the configuration values to make sure that they
        // correctly authenticate with Core
        testAuth();
    }

    /*---------------------------------------------------------------------------------------------
     * IMPLEMENTATION METHODS
     *-------------------------------------------------------------------------------------------*/

    @Override
    public Count count(BridgeRequest request) throws BridgeError {
        String structure = request.getStructure();
        if (!VALID_STRUCTURES.contains(structure)) {
            throw new BridgeError("Invalid Structure: '" + structure + "' is not a valid structure");
        }

        JiraQualificationParser parser = new JiraQualificationParser();
        String query = parser.parse(request.getQuery(),request.getParameters());

        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append(String.format("%s/rest/api/latest/",this.baseSite));
        if (structure.equals("Project")) {
            String wildcardFreeQuery = createWildcardFreeQuery(query);
            if (!wildcardFreeQuery.equals("")) {
                throw new BridgeError("Invalid Query: The 'Project' structure cannot filter out results, so the query must in the form of '*' or 'field=*'");
            }
            queryBuilder.append("project");
        } else if (structure.equals("Group")) {
            queryBuilder.append("groups/picker?");
            String wcFreeQuery = createWildcardFreeQuery(query);
            queryBuilder.append(URLEncoder.encode(wcFreeQuery));
        } else if (structure.equals("User")) {
            queryBuilder.append("user/search?");
            queryBuilder.append(URLEncoder.encode(query));
        } else if (structure.equals("Issue")) {
            queryBuilder.append("search?");

            queryBuilder.append("fields=key");
            queryBuilder.append("&jql=");

            String wcFreeQuery = createWildcardFreeQuery(query);
            queryBuilder.append(URLEncoder.encode(wcFreeQuery));
        }

        JSONArray jsonArray;
        Long count;
        ResponseData response = sendRequest(queryBuilder.toString());
        if (response.getStatusCode() != 200) {
            logger.error(response.getOutput());
            throw new BridgeError(response.getErrorMessage());
        }
        if (structure.equals("Group") || structure.equals("Issue")) {
            JSONObject jsonOutput = (JSONObject)JSONValue.parse(response.getOutput());
            count = Long.valueOf(jsonOutput.get("total").toString());
        } else { // If structure is equal to User or Project
            jsonArray = (JSONArray)JSONValue.parse(response.getOutput());
            count = Long.valueOf(jsonArray.size());
        }

        return new Count(count);
    }

    @Override
    public Record retrieve(BridgeRequest request) throws BridgeError {
        request.setQuery(substituteQueryParameters(request));

        String structure = request.getStructure();
        if (!VALID_STRUCTURES.contains(structure)) {
            throw new BridgeError("Invalid Structure: '" + structure + "' is not a valid structure");
        }

        JiraQualificationParser parser = new JiraQualificationParser();
        String query = parser.parse(request.getQuery(),request.getParameters());
        List<String> fields = request.getFields();

        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append(String.format("%s/rest/api/latest/", this.baseSite));
        if (structure.equals("Issue")) {
            queryBuilder.append("search?");
            StringBuilder simpleFields = new StringBuilder();
            for (String field : fields) {
                simpleFields.append(field.split("\\.")[0]);
                simpleFields.append(",");
            }

            queryBuilder.append("fields=");
            queryBuilder.append(simpleFields.toString());
            queryBuilder.append("&maxResults=1");
            queryBuilder.append("&jql=");
            queryBuilder.append(URLEncoder.encode(query));
        } else if (structure.equals("Project")) {
            String parameter = query.split("&")[0];
            String value = parameter.split("=")[1];
            queryBuilder.append("project/");
            queryBuilder.append(value);
        } else if (structure.equals("User")) {
            queryBuilder.append("user?");
            queryBuilder.append(URLEncoder.encode(query));
        } else if (structure.equals("Group")) {
            queryBuilder.append("group?");
            queryBuilder.append(URLEncoder.encode(query));
            queryBuilder.append("&expand=users");
        }

        Long count;
        ResponseData response = sendRequest(queryBuilder.toString());
        JSONObject jsonOutput = (JSONObject)JSONValue.parse(response.getOutput());
        if (response.getStatusCode() != 200 && response.getStatusCode() != 404) {
            logger.error(response.getOutput());
            throw new BridgeError(response.getErrorMessage());
        }

        if (response.getStatusCode() == 404) {
            count = 0L;
        } else if (structure.equals("Issue")) {
            count = (Long)jsonOutput.get("total");
            if (count != 0L) {
                JSONArray jsonArray = (JSONArray)jsonOutput.get("issues");
                jsonOutput = (JSONObject)jsonArray.get(0);
            }
        } else {
            count = 1L;
        }

        Map<String,Object> record = null;

        if (count > 1L) {
            throw new BridgeError("Multiple results matched an expected single match query");
        } else if (count == 1L) {
            JSONObject fieldObject = null;
            record = new LinkedHashMap<String,Object>();
            for (String field : fields) {
                Object value;
                if (jsonOutput.keySet().contains(field)) {
                    value = jsonOutput.get(field);
                } else if (!jsonOutput.keySet().contains("fields")) {
                    throw new BridgeError("Invalid Field: The field '" + field + "' was not found in the '" + structure + "' object.");
                } else {
                    if (fieldObject == null) {
                        fieldObject = (JSONObject)JSONValue.parse(jsonOutput.get("fields").toString());
                    }
                    value = getFieldObject(fieldObject,field);
                }
                record.put(field,value);
            }
        }
        return new Record(record);
    }

    @Override
    public RecordList search(BridgeRequest request) throws BridgeError {
        request.setQuery(substituteQueryParameters(request));

        // Initialize the result data and response variables
        Map<String,Object> data = new LinkedHashMap();

        String structure = request.getStructure();
        if (!VALID_STRUCTURES.contains(structure)) {
            throw new BridgeError("Invalid Structure: '" + structure + "' is not a valid structure");
        }

        JiraQualificationParser parser = new JiraQualificationParser();
        String query = parser.parse(request.getQuery(),request.getParameters());
        List<String> fields = request.getFields();

        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append(String.format("%s/rest/api/latest/",this.baseSite));
        if (structure.equals("Project")) {
            String wildcardFreeQuery = createWildcardFreeQuery(query);
            if (!wildcardFreeQuery.equals("")) {
                throw new BridgeError("Invalid Query: The 'Project' structure cannot filter out results, so the query must in the form of '*' or 'field=*'");
            }
            queryBuilder.append("project");
        } else if (structure.equals("Group")) {
            queryBuilder.append("groups/picker?");
            String wcFreeQuery = createWildcardFreeQuery(query);
            queryBuilder.append(URLEncoder.encode(wcFreeQuery));
        } else if (structure.equals("User")) {
            queryBuilder.append("user/search?");
            queryBuilder.append(URLEncoder.encode(query));
        } else if (structure.equals("Issue")) {
            queryBuilder.append("search?");
            StringBuilder simpleFields = new StringBuilder();
            for (String field : fields) {
                simpleFields.append(field.split("\\.")[0]);
                simpleFields.append(",");
            }

            queryBuilder.append("fields=");
            queryBuilder.append(simpleFields.toString());
            queryBuilder.append("&jql=");
            String wcFreeQuery = createWildcardFreeQuery(query);
            queryBuilder.append(URLEncoder.encode(wcFreeQuery));

            String[] standardSortFields = new String[] {"key","id","summary","issuetype","votes","resolution","resolutiondate","reporter","updated","created","description","priority","duedate","subtasks",
                                                            "status","labels","assignee","project","environment","lastViewed","progress","timeestimate","timeoriginalestimate","timespent","workratio"};

            // Creating a order by string to add to the jql query.
            StringBuilder order = new StringBuilder();
            // Id and Key are considered aliases but have different values, so
            // if one is added the other won't be added so that an error is not thrown.
            Boolean keyAppended = false;
            if (request.getMetadata("order") != null) {
                for (Map.Entry<String,String> entry : BridgeUtils.parseOrder(request.getMetadata("order")).entrySet()) {
                    String key = entry.getKey();
                    if (!((key.equals("key") || key.equals("id")) && keyAppended == true)) {
                        if (key.equals("key") || key.equals("id")) {
                            keyAppended = true;
                        }
                        if (entry.getValue().equals("DESC")) {
                            key = key + " desc";
                        }
                        if (order.toString().isEmpty()) {
                            order.append("order by ");
                        } else {
                            order.append(",");
                        }
                        order.append(key);
                    }
                }
            } else {
                for (String field : fields) {
                    String simpleField = field.split("//.")[0];
                    if (!((simpleField.equals("key") || simpleField.equals("id")) && keyAppended == true)) {
                        if (simpleField.equals("key") || simpleField.equals("id")) {
                            keyAppended = true;
                        }
                        if (Arrays.asList(standardSortFields).contains(simpleField)) {
                            if (order.toString().isEmpty()) {
                                order.append("order by ");
                            } else {
                                order.append(",");
                            }
                            order.append(simpleField);
                        }
                    }
                }
            }
            queryBuilder.append("+").append(URLEncoder.encode(order.toString()));
        }

        // Appending information about page sizes
        setDefaultMetadata(request);
        if (structure.equals("Project")) {
            if (!request.getMetadata("offset").equals("0") || !request.getMetadata("pageSize").equals("0")) {
                throw new BridgeError("Invalid Pagination Data: Pagination is not allowed with the 'Project' structure.");
            }
        } else if (structure.equals("Group")) {
            if (!request.getMetadata("offset").equals("0")) {
                throw new BridgeError("Invalid Pagination Data: The 'Group' structure only allows 1 page of data (setting just a size for that page is allowed though).");
            }
            if (!request.getMetadata("pageSize").equals("0")) {
                queryBuilder.append("&maxResults=").append(request.getMetadata("pageSize"));
            }
        } else {
            if (!request.getMetadata("pageSize").equals("0")) {
                queryBuilder.append("&maxResults=").append(request.getMetadata("pageSize"));
                queryBuilder.append("&startsAt=").append(request.getMetadata("offset"));
            }
        }

        JSONArray jsonArray;
        Long count;
        ResponseData response = sendRequest(queryBuilder.toString());

        if (response.getStatusCode() != 200) {
            logger.error(response.getOutput());
            throw new BridgeError(response.getErrorMessage());
        }

        if (structure.equals("Group")) {
            JSONObject jsonOutput = (JSONObject)JSONValue.parse(response.getOutput());
            jsonArray = (JSONArray)jsonOutput.get("groups");
            count = Long.valueOf(jsonOutput.get("total").toString());
        } else if (structure.equals("Issue")) {
            JSONObject jsonOutput = (JSONObject)JSONValue.parse(response.getOutput());
            jsonArray = (JSONArray)jsonOutput.get("issues");
            count = Long.valueOf(jsonOutput.get("total").toString());
        } else { // If structure is equal to User or Project
            jsonArray = (JSONArray)JSONValue.parse(response.getOutput());
            count = Long.valueOf(jsonArray.size());
        }

        // Parse through the response and create the record lists
        ArrayList<Record> records = new ArrayList<Record>();
        for (int i=0; i < jsonArray.size(); i++) {
            Map<String,Object> record = new HashMap<String,Object>();
            JSONObject recordObject = (JSONObject)jsonArray.get(i);
            JSONObject fieldObject = null;
            for (String field : fields) {
                Object value;
                if (recordObject.keySet().contains(field)) {
                   value = recordObject.get(field);
                } else if (!recordObject.keySet().contains("fields")) {
                    throw new BridgeError("Invalid Field: The field '" + field + "' was not found in the '" + structure + "' object.");
                } else {
                    if (fieldObject == null) {
                        fieldObject = (JSONObject)JSONValue.parse(recordObject.get("fields").toString());
                    }
                    value = getFieldObject(fieldObject,field);
                }
                record.put(field,value);
            }
            records.add(new Record(record));
        }

        if (!structure.equals("Issue")) {
            if (request.getMetadata("order") == null) {
                // name,type,desc assumes name ASC,type ASC,desc ASC
                Map<String,String> defaultOrder = new LinkedHashMap<String,String>();
                for (String field : fields) {
                    defaultOrder.put(field, "ASC");
//                    Map<String,String> defaultOrder = BridgeUtils.parseOrder(field);
                }
                records = sortRecords(defaultOrder, records);
            } else {
            // Creates a map out of order metadata
              Map<String,String> orderParse = BridgeUtils.parseOrder(request.getMetadata("order"));
              records = sortRecords(orderParse, records);
            }
        }

        // Building the output metadata
        Map<String,Long> metadata = new LinkedHashMap();
        metadata.put("pageSize", Long.valueOf(request.getMetadata("pageSize")));
        metadata.put("pageNumber", Long.valueOf(request.getMetadata("pageNumber")));
        metadata.put("offset", Long.valueOf(request.getMetadata("offset")));
        metadata.put("size", Long.valueOf(records.size()));
        metadata.put("count", count);

        data.put("metadata",metadata);
        data.put("fields",fields);
        data.put("records",records);
        System.out.println(records);

        // Return the response
        return new RecordList(request.getFields(), records);
    }

    /*---------------------------------------------------------------------------------------------
     * HELPER METHODS
     *-------------------------------------------------------------------------------------------*/

    private String substituteQueryParameters(BridgeRequest request) throws BridgeError {
        JiraQualificationParser parser = new JiraQualificationParser();
        return parser.parse(request.getQuery(),request.getParameters());
    }

    private String createWildcardFreeQuery(String query) throws BridgeError {
        StringBuilder output = new StringBuilder();

        String[] queryParams = query.split("&");
        for (String param : queryParams) {
            String[] keyValuePair = param.split("=");
            if (keyValuePair.length == 1) {
                if (!keyValuePair[0].trim().equals("*")) {
                    output.append(param);
                }
            } else if (!keyValuePair[1].trim().equals("*")) {
                output.append(param);
            }
        }

        return output.toString();
    }

    private void testAuth() throws BridgeError {
        logger.debug("Testing the authentication credentials");
        HttpGet get = new HttpGet(baseSite + "/rest/api/latest/");
        get = addAuthenticationHeader(get, this.username, this.password);

        HttpClient client = HttpClients.createDefault();
        HttpResponse response;
        try {
            response = client.execute(get);
            HttpEntity entity = response.getEntity();
            EntityUtils.consume(entity);
            if (response.getStatusLine().getStatusCode() == 401) {
                throw new BridgeError("Unauthorized: The Username/Password combination is not valid.");
            }
        }
        catch (IOException e) {
            logger.error(e.getMessage());
            throw new BridgeError("Unable to make a connection to Jira.");
        }
    }

    private HttpGet addAuthenticationHeader(HttpGet get, String username, String password) {
        String creds = username + ":" + password;
        byte[] basicAuthBytes = Base64.encodeBase64(creds.getBytes());
        get.setHeader("Authorization", "Basic " + new String(basicAuthBytes));

        return get;
    }

    private Object getFieldObject(JSONObject fieldObject, String complexObject) throws BridgeError {
        Object value = "";

        // Parsing the complexObject
        String[] field = complexObject.split("\\.");

        if (field[0].equals("fixVersions")) {
            JSONArray fixVersions = (JSONArray)fieldObject.get("fixVersions");
            ArrayList<String> fvArray = new ArrayList<String>();
            for (int versionIndex = 0; versionIndex < fixVersions.size(); versionIndex++) {
                JSONObject versionObject = (JSONObject)fixVersions.get(versionIndex);
                fvArray.add(versionObject.get(field[1]).toString());
            }
            value = StringUtils.join(fvArray.toArray(),",");
        } else {
            JSONObject nextObject = fieldObject;
            for (int j = 0; j < field.length; j++) {
                if (nextObject.containsKey(field[j])) {
                    if (j == field.length -1) {
                        value = nextObject.get(field[j]);
                    } else {
                        // Separating getting the next object from the json conversion process in case
                        // the next object is null, which lets the adapter avoid attempting to parse a null pointer
                        Object obj = nextObject.get(field[j]);
                        if (obj == null) {
                            value = "";
                            break;
                        } else {
                            nextObject = (JSONObject)JSONValue.parse(obj.toString());
                        }
                    }
                } else {
                    throw new BridgeError("Invalid Complex Object: The complex object of '" + complexObject + "' could not be returned because the field '" + field[j] + "' could not be found.");
                }
            }
        }

        return value;
    }

    /**
     * A method that is used to send the http request to jira when given a query string
     */
    private ResponseData sendRequest(String queryString) throws BridgeError {
        String output = "";
        ResponseData responseData;

        HttpClient client = HttpClients.createDefault();
        HttpGet get = new HttpGet(queryString);

        // Setting up the basic authentication
        logger.trace("Appending the authorization header to the post call");
        String creds = this.username + ":" + this.password;
        byte[] basicAuthBytes = Base64.encodeBase64(creds.getBytes());
        get.setHeader("Authorization", "Basic " + new String(basicAuthBytes));

        HttpResponse response;
        try {
            // Sending the request and timing how long it takes to complete
            long startTime = System.nanoTime();
            response = client.execute(get);
            long endTime = System.nanoTime();
            long methodTime = endTime - startTime;
            logger.trace("HttpGet length: " + String.valueOf((double)methodTime / 1000000000.0) + " seconds");

            // Parsing the response that is retrieved
            HttpEntity entity = response.getEntity();
            output = EntityUtils.toString(entity);
        }
        catch (IOException e) {
            throw new BridgeError("IOException: Unable to make connection to " + queryString, e);
        }

        logger.debug("Query String: " + queryString);

        Integer statusCode = response.getStatusLine().getStatusCode();
        responseData = new ResponseData(statusCode, output);

        if (statusCode != 200) {
            logger.error(output);
            if (statusCode == 401) {
                responseData.setErrorMessage("Unauthorized Access: Please check your username and password and try again.");
            } else {
                StringBuilder fullMessage = new StringBuilder();
                try {
                    JSONObject jsonOutput = (JSONObject)JSONValue.parse(output);
                    JSONArray errorMessages = (JSONArray)jsonOutput.get("errorMessages");
                    for (int i=0; i < errorMessages.size(); i++) {
                        if (i != 0) {
                            fullMessage.append(", ");
                        }
                        fullMessage.append(errorMessages.get(i).toString());
                    }
                } catch (Exception e) {
                    responseData.setErrorMessage("An unexpected error has occurred. See the log for more details.");
                }
                responseData.setErrorMessage(fullMessage.toString());
            }
        }

        logger.trace("Jira Response: " + output);

        return responseData;
    }

    protected ArrayList<Record> sortRecords(final Map<String,String> fieldParser, ArrayList<Record> records) throws BridgeError {
        Collections.sort(records, new Comparator<Record>() {
            @Override
            public int compare(Record r1, Record r2){
                CompareToBuilder comparator = new CompareToBuilder();

                for (Map.Entry<String,String> entry : fieldParser.entrySet()) {
                    String field = entry.getKey();
                    String order = entry.getValue();

                    Object o1 = r1.getValue(field);
                    if (o1.getClass() == String.class) {o1 = o1.toString().toLowerCase();}

                    Object o2 = r2.getValue(field);
                    if (o2.getClass() == String.class) {o2 = o2.toString().toLowerCase();}

                    if (order.equals("DESC")) {
                        comparator.append(o2,o1);
                    } else {
                        comparator.append(o1,o2);
                    }
                }

                return comparator.toComparison();
            }
        });
        return records;
    }

    protected void setDefaultMetadata(BridgeRequest request) throws BridgeError{
        // Intialize the Long variables that will eventually be returned by
        // the metadata
        Long pageSize;
        Long pageNumber;
        Long offset;

        // Converting the inputted metadata from a string to a Long, and if
        // there is no inputted value a logical default value is chosen
        if (request.getMetadata("pageSize") == null) {
            pageSize = 0L;
        } else {
            pageSize = Long.valueOf(request.getMetadata("pageSize"));
        }

        if (request.getMetadata("pageNumber") == null) {
            pageNumber = 1L;
        } else {
            pageNumber = Long.valueOf(request.getMetadata("pageNumber"));
        }

        if (request.getMetadata("offset") == null) {
            offset = ((pageNumber-1)*pageSize);
        } else {
            offset = Long.valueOf(request.getMetadata("offset"));
        }

        // Validate that the inputted metadata is logically usable
        if (request.getMetadata("pageNumber") != null && request.getMetadata("pageSize") == null) {
            throw new BridgeError("Illegal search, the pageNumber metadata value was passed without specifying a pageSize.");
        }
        if (request.getMetadata("pageNumber") == null && request.getMetadata("pageSize") != null) {
            throw new BridgeError("Illegal search, the pageSize metadata value was passed without specifying a pageNumber.");
        }
        if(request.getMetadata("pageSize") != null && request.getMetadata("offset") != null && offset != (pageNumber-1L)*pageSize) {
            throw new BridgeError("Illegal search, the offset does not match the specified pageSize and pageNumber.");
        }
        // Validate that the offset is not over 2000, which is the limit for
        // SOQL queries
        if (offset >= 2000L) {
            throw new BridgeError("Offset is calculated to be greater than or equal to 2000. SOQL Query Limit reached.");
        }

        Map<String,String> metadata;
        // Building up and returning the metadata
        if (request.getMetadata() != null) {
            metadata = request.getMetadata();
        }
        else {
            metadata = new LinkedHashMap();
        }
        metadata.put("pageNumber",pageNumber.toString());
        metadata.put("pageSize",pageSize.toString());
        metadata.put("offset",offset.toString());
        request.setMetadata(metadata);
    }

    final class ResponseData {
        private Integer statusCode;
        private String errorMessage;
        private String output;

        public ResponseData(Integer statusCode, String output) {
            this.statusCode = statusCode;
            this.output = output;
        }

        public Integer getStatusCode() {
            return this.statusCode;
        }

        public void setStatusCode(Integer statusCode) {
            this.statusCode = statusCode;
        }

        public String getErrorMessage() {
            return this.errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public String getOutput() {
            return this.output;
        }

        public void setOutput(String output) {
            this.output = output;
        }
    }
}