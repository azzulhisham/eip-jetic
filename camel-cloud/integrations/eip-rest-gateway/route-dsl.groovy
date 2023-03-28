//
// To run this integration use:
//
// kamel run eip_rest_gateway.groovy
//

// camel-k: language=groovy
// camel-k: name=eip-rest-gateway

// import org.apache.camel.Message
import com.datagrate.messagehistory.DataAnalyzer
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.apache.camel.model.dataformat.JsonLibrary
import org.json.JSONObject;
import org.json.XML;

// activate JETIC.IO Data Analyzer
DataAnalyzer.activate(getContext(), intercept(), onException(Throwable.class))

from('direct://default').routeId('route-1')
    .setBody().simple('This is a EIP Gateway for MMDIS, LRIT and DDMS.').id('setBody-00')

from('direct://ddms').routeId('route-2')
    .to('sql:SELECT sl.ShipListID, sl.IMO, sl.CallSign, sl.Name, sl.ShipType, sl.Dimension_A, sl.Dimension_B, sl.Dimension_C, sl.Dimension_D, sl.MaxDraught, sl.Destination, sl.ETA, sl.EquipTypeID, sl.ESN, sl.DNID, sl.MemberNumber, ddms.*  FROM [VTS].[dbo].[ShipList] sl INNER JOIN [VTS].[dbo].[Draught] ddms on ddms.MMSI = sl.MMSI?batch=false&dataSource=#enavSqlServer&useMessageBodyForSql=false').id('sql-00')
    .marshal().json(JsonLibrary.Jackson).id('toJson-00')

from('direct://lrit get ship info').routeId('route-3')
    .setBody().simple('${header.imo}').id('setBody-01')
    .process(new Processor() {
        @Override
        void process(Exchange exchange) throws Exception {
            // Providing the website URL
            URL url = new URL("http://lrit.com.my/ASPPositionWebServices/service.asmx");
        
            // Creating an HTTP connection
            HttpURLConnection MyConn = (HttpURLConnection) url.openConnection();
            // Set the request method to "GET"
            //MyConn.setRequestMethod("GET");
            MyConn.setRequestMethod("POST");
            MyConn.setDoOutput(true);
            MyConn.setRequestProperty("Content-Type","text/xml");
            MyConn.setRequestProperty("SOAPAction", "\"http://LRIT.svc/GetShipInfo\"");
            //MyConn.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString((userName + ":" + password).getBytes()));
        
            String imo = exchange.getIn().getBody(String.class);
        
            String payload = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                                                        "<soap:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
                                                        "<soap:Body>" +
                                                        "<GetShipInfo xmlns=\"http://LRIT.svc/\">" +
                                                        "<IMONumber>" + imo + "</IMONumber>" +
                                                        "</GetShipInfo>" +
                                                        "</soap:Body>" +
                                                        "</soap:Envelope>";
        
        
            byte[] out = payload.getBytes(StandardCharsets.UTF_8);
            OutputStream stream = MyConn.getOutputStream();
            stream.write(out);
        
            int responseCode = MyConn.getResponseCode();
            //System.out.println("GET Response Code :: " + responseCode);
        
            if (responseCode == MyConn.HTTP_OK) {
            	// Create a reader with the input stream reader.
            	BufferedReader in = new BufferedReader(new InputStreamReader(
            		MyConn.getInputStream()));
            	String inputLine;
        
            	// Create a string buffer
            	StringBuffer response = new StringBuffer();
        
            	// Write each of the input line
            	while ((inputLine = in.readLine()) != null) {
            		response.append(inputLine);
            	}
            	in.close();
        
            	String xml = response.toString();
        
            	byte[] encoded = xml.getBytes();
            	JSONObject xmlJSONObj = XML.toJSONObject(new String(encoded));
            	//String json = xmlJSONObj.toString(4);
        
            	JSONObject envelope = xmlJSONObj.getJSONObject("soap:Envelope");
            	JSONObject body = envelope.getJSONObject("soap:Body");
        
            	JSONObject result = new JSONObject();
            	result.put("Body", body);
        
            	exchange.getIn().setBody(result.toString(4));
        
            } else {
            	System.out.println("Error found !!!");
            }
        }
    }).id('process-00')

from('direct://lrit get ships position').routeId('route-4')
    .process(new Processor() {
        @Override
        void process(Exchange exchange) throws Exception {
            Object startDate = exchange.getIn().getHeader("startDate");
            Object endDate = exchange.getIn().getHeader("endDate");
        
            URL url = new URL("http://lrit.com.my/ASPPositionWebServices/service.asmx");
        
            // Creating an HTTP connection
            HttpURLConnection MyConn = (HttpURLConnection) url.openConnection();
            // Set the request method to "GET"
            //MyConn.setRequestMethod("GET");
            MyConn.setRequestMethod("POST");
            MyConn.setDoOutput(true);
            MyConn.setRequestProperty("Content-Type","text/xml");
            MyConn.setRequestProperty("SOAPAction", "\"http://LRIT.svc/GetPositions\"");
            //MyConn.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString((userName + ":" + password).getBytes()));
        
        
            String payload = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            	"<soap:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
            	"<soap:Body>" +
            	"<GetPositions xmlns=\"http://LRIT.svc/\">" +
            	"<from>" + startDate + "</from>" +
            	"<to>" + endDate + "</to>" +
            	"</GetPositions>" +
            	"</soap:Body>" +
            	"</soap:Envelope>";
        
            byte[] out = payload.getBytes(StandardCharsets.UTF_8);
            OutputStream stream = MyConn.getOutputStream();
            stream.write(out);
        
            int responseCode = MyConn.getResponseCode();
            //System.out.println("GET Response Code :: " + responseCode);
        
            if (responseCode == MyConn.HTTP_OK) {
            	// Create a reader with the input stream reader.
            	BufferedReader in = new BufferedReader(new InputStreamReader(
            		MyConn.getInputStream()));
            	String inputLine;
        
            	// Create a string buffer
            	StringBuffer response = new StringBuffer();
        
            	// Write each of the input line
            	while ((inputLine = in.readLine()) != null) {
            		response.append(inputLine);
            	}
            	in.close();
        
            	String xml = response.toString();
            	byte[] encoded = xml.getBytes();
            	JSONObject xmlJSONObj = XML.toJSONObject(new String(encoded));
            	String json = xmlJSONObj.toString(4);
        
            	JSONObject envelope = xmlJSONObj.getJSONObject("soap:Envelope");
            	JSONObject body = envelope.getJSONObject("soap:Body");
        
            	JSONObject result = new JSONObject();
            	result.put("Body", body);
        
            	exchange.getIn().setBody(result.toString(4));
            } else {
            	System.out.println("Error found !!!");
            }
        
        }
    }).id('process-01')

from('direct://mmdis get vessel').routeId('route-5')
    .process(new Processor() {
        @Override
        void process(Exchange exchange) throws Exception {
            Object imo = exchange.getIn().getHeader("imo");
            System.out.println("imo :: " + imo);
            exchange.getIn().setBody("");
        
            URL url = new URL("http://mdm.enav.my:50837/api/MMDIS/vessel/" + imo);
        
            HttpURLConnection MyConn = (HttpURLConnection) url.openConnection();
            // Set the request method to "GET"
            MyConn.setRequestMethod("GET");
            MyConn.setDoOutput(true);
            MyConn.setRequestProperty("Content-Type","application/json");
            //MyConn.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString((userName + ":" + password).getBytes()));
        
        
            //String payload = "{" +
            //									"\"vesselId\": " + "\"\"," +
            //									"\"vesselName\": " + "\"\"," +
            //									"\"officialNumber\": " + "\"\"," +
            //									"\"imoNumber\": " + "\"" + imo +  "\"" +
            //									"}";
        
            //byte[] out = payload.getBytes(StandardCharsets.UTF_8);
            //OutputStream stream = MyConn.getOutputStream();
            //stream.write(out);
        
            int responseCode = MyConn.getResponseCode();
            System.out.println("GET Response Code :: " + responseCode);
        
            if (responseCode == MyConn.HTTP_OK) {
            	// Create a reader with the input stream reader.
            	BufferedReader in = new BufferedReader(new InputStreamReader(
            		MyConn.getInputStream()));
            	String inputLine;
        
            	// Create a string buffer
            	StringBuffer response = new StringBuffer();
        
            	// Write each of the input line
            	while ((inputLine = in.readLine()) != null) {
            		response.append(inputLine);
            	}
            	in.close();
        
            	String resp = response.toString();
        
            	exchange.getIn().setBody(resp);
            } else {
            	// Create a reader with the input stream reader.
            	BufferedReader in = new BufferedReader(new InputStreamReader(
            		MyConn.getInputStream()));
            	String inputLine;
        
            	// Create a string buffer
            	StringBuffer response = new StringBuffer();
        
            	// Write each of the input line
            	while ((inputLine = in.readLine()) != null) {
            		response.append(inputLine);
            	}
            	in.close();
        
            	String resp = response.toString();
        
            	exchange.getIn().setBody(resp);
            }
        
        }
    }).id('process-02')