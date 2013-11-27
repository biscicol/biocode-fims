package settings;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.net.CookieManager;
import java.util.List;


/**
 * General purpose class for authenticating against BCID system and creating BCIDs.
 */
public class bcidConnector {

    private List<String> cookies;
    //private HttpURLConnection conn;
    private final String USER_AGENT = "Mozilla/5.0";
    private final String ACCEPT_LANGUAGE = "en-US,en;q=0.5";
    private final String HOST = "biscicol.org";
    private final String ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
    private final String CONTENT_TYPE = "application/x-www-form-urlencoded";
    private final String CONNECTION = "keep-alive";

    private String authenticationURL = "http://biscicol.org/bcid/j_spring_security_check";
    private String arkCreationURL = "http://biscicol.org/id/groupService";
    private String connectionPoint;

    public static void main(String[] args) {

        bcidConnector bcid = new bcidConnector();
        String message = null;

        // Authenticate
        boolean success = false;
        try {
            success = bcid.authenticate("demo", "demo");
            if (success)
                System.out.println("Able to authenticate!");
        } catch (Exception e) {
            message = e.getMessage();
            e.printStackTrace();
        }


        // Success then create ARK
        if (success)
            try {
                message = "Created BCID = " + bcid.createBCID(null);
            } catch (Exception e) {
                message = e.getMessage();
                e.printStackTrace();
            }
        else
            message = "Unable to authenticate";

        System.out.println(message);

    }

    /**
     * the constructor calls the CookieManager, used while the instantiated object is alive for authentication
     */
    public bcidConnector() {
        // make sure cookies is turn on
        CookieHandler.setDefault(new CookieManager());
    }

    /**
     * Authenticate against BCID system.  This is done first to set cookies in this class in the cookies class variable.
     *
     * @param username
     * @param password
     * @return
     * @throws Exception
     */
    public boolean authenticate(String username, String password) throws Exception {

        String postParams = "j_username=" + username + "&j_password=" + password;
        URL url = new URL(authenticationURL);
        String response = createPOSTConnnection(url, postParams);

        // TODO: use a proper method for determining response/error codes (like HTTP Response code = 401 or 403)!
        if (response.toString().contains("Bad credentials")) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Create a BCID.  Uses cookies sent during authentication method.
     *
     * @return
     * @throws Exception
     */
    public String createBCID(String webaddress) throws Exception {
        String createBCIDDatasetPostParams =
                "title=Loaded Dataset from Biocode-FIMS&" +
                "resourceTypesMinusDataset=1&" +
                "suffixPassThrough=false&" +
                "webaddress=" + webaddress;

        URL url = new URL(arkCreationURL);
        String response = createPOSTConnnection(url, createBCIDDatasetPostParams);

        return response.toString();
    }

    /**
     * Generic method for creating a POST connection for which to talk to BCID services
     *
     * @param url
     * @param postParams
     * @return
     * @throws IOException
     */
    private String createPOSTConnnection(URL url, String postParams) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        // Acts like a browser
        conn.setUseCaches(false);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Host", HOST);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", ACCEPT);
        conn.setRequestProperty("Accept-Language", ACCEPT_LANGUAGE);
        if (cookies != null) {
            for (String cookie : this.cookies) {
                conn.addRequestProperty("Cookie", cookie.split(";", 1)[0]);
            }
        }

        conn.setRequestProperty("Connection", CONNECTION);
        //conn.setRequestProperty("Referer", "https://accounts.google.com/ServiceLoginAuth");
        conn.setRequestProperty("Content-Type", CONTENT_TYPE);
        conn.setRequestProperty("Content-Length", Integer.toString(postParams.length()));

        conn.setDoOutput(true);
        conn.setDoInput(true);

        // Send post request
        DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
        wr.writeBytes(postParams);
        wr.flush();
        wr.close();

        /*
        int responseCode = conn.getResponseCode();
        System.out.println("\nSending 'POST' request to URL : " + authenticationURL);
        System.out.println("Post parameters : " + postParams);
        System.out.println("Response Code : " + responseCode);
        */

        BufferedReader in =
                new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();
        // Get the response cookies
        setCookies(conn.getHeaderFields().get("Set-Cookie"));
        return response.toString();
    }

    /**
     * Custom BCID GET connection example
     *
     * @param url
     * @return
     * @throws IOException
     */
    private String createGETConnection(URL url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        // default is GET
        conn.setRequestMethod("GET");
        conn.setUseCaches(false);

        // act like a browser
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
        if (cookies != null) {
            for (String cookie : this.cookies) {
                conn.addRequestProperty("Cookie", cookie.split(";", 1)[0]);
            }
        }
        int responseCode = conn.getResponseCode();
        System.out.println("\nSending 'GET' request to URL : " + arkCreationURL);
        System.out.println("Response Code : " + responseCode);

        BufferedReader in =
                new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        // Get the response cookies
        setCookies(conn.getHeaderFields().get("Set-Cookie"));
        return response.toString();
    }

    /**
     * Return Cookies
     *
     * @return
     */
    public List<String> getCookies() {
        return cookies;
    }

    /**
     * Set Cookies
     *
     * @param cookies
     */
    public void setCookies(List<String> cookies) {
        this.cookies = cookies;
    }

}