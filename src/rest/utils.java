package rest;

import digester.Field;
import digester.Validation;
import org.apache.commons.digester3.Digester;
import run.configurationFileFetcher;
import run.process;
import run.processController;
import settings.FIMSRuntimeException;
import settings.bcidConnector;
import utils.SettingsManager;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Iterator;


/**
 * Biocode-FIMS utility services
 */
@Path("utils/")
public class utils {
    @Context
    static ServletContext context;

    /**
     * Refresh the configuration File cache
     *
     * @return
     */
    @GET
    @Path("/refreshCache/{project_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response queryJson(
            @QueryParam("project_id") Integer project_id) {

        new configurationFileFetcher(project_id, uploadPath(), false).getOutputFile();

        return Response.ok("").build();

    }


    /**
     * Get real path of the uploads folder from context.
     * Needs context to have been injected before.
     *
     * @return Real path of the uploads folder with ending slash.
     */
    static String uploadPath() {
        return context.getRealPath("tripleOutput") + File.separator;
    }

    /**
     * Retrieve a user's expeditions in a given project from bcid. This uses an access token to access the
     * bcid service.
     *
     * @param projectId
     *
     * @return
     */
    @GET
    @Path("/expeditionCodes/{project_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getExpeditionCodes(@PathParam("project_id") Integer projectId,
                                       @Context HttpServletRequest request) {
        HttpSession session = request.getSession();
        String accessToken = (String) session.getAttribute("access_token");
        String refreshToken = (String) session.getAttribute("refresh_token");
        bcidConnector bcidConnector = new bcidConnector(accessToken, refreshToken);

        String response = bcidConnector.getExpeditionCodes(projectId);

        return Response.status(bcidConnector.getResponseCode()).entity(response).build();
    }

    /**
     * Retrieve a user's graphs in a given project from bcid. This uses an access token to access the
     * bcid service.
     *
     * @param projectId
     *
     * @return
     */
    @GET
    @Path("/graphs/{project_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getGraphs(@PathParam("project_id") Integer projectId,
                              @Context HttpServletRequest request) {
        HttpSession session = request.getSession();
        String accessToken = (String) session.getAttribute("access_token");
        String refreshToken = (String) session.getAttribute("refresh_token");
        bcidConnector bcidConnector = new bcidConnector(accessToken, refreshToken);

        String response = bcidConnector.getGraphs(projectId);

        return Response.status(bcidConnector.getResponseCode()).entity(response).build();
    }

    /**
     * Check whether or not an expedition code is valid by calling the BCID expeditionService/validateExpedition
     * Service
     * Should return update, insert, or error
     *
     * @param projectId
     * @param expeditionCode
     * @param request
     *
     * @return
     */
    @GET
    @Path("/validateExpedition/{project_id}/{expedition_code}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response validateExpedition(@PathParam("project_id") Integer projectId,
                                       @PathParam("expedition_code") String expeditionCode,
                                       @Context HttpServletRequest request) {


        HttpSession session = request.getSession();
        String accessToken = (String) session.getAttribute("access_token");
        String refreshToken = (String) session.getAttribute("refresh_token");
        bcidConnector bcidConnector = new bcidConnector(accessToken, refreshToken);

        SettingsManager sm = SettingsManager.getInstance();
        sm.loadProperties();
        String expedition_list_uri = sm.retrieveValue("expedition_validation_uri");

        URL url;
        try {
            url = new URL(expedition_list_uri +
                    projectId + "/" +
                    expeditionCode +
                    "?access_token=" + accessToken);
        } catch (MalformedURLException e) {
            throw new FIMSRuntimeException(500, e);
        }

        String response = bcidConnector.createGETConnection(url);

        // Debugging
        System.out.println("FIMS validateExpedition code = " + bcidConnector.getResponseCode());
        System.out.println("FIMS validateExpedition response = " + response);

        return Response.status(bcidConnector.getResponseCode()).entity(response).build();
    }



    /**
     * Retrieve a user's expeditions in a given project from bcid. This uses an access token to access the
     * bcid service.
     *
     * @param projectId
     *
     * @return
     */
    @GET
    @Path("/getListFields/{list_name}/")
    @Produces(MediaType.TEXT_HTML)
    public Response getListFields(@QueryParam("project_id") Integer projectId,
                                  @PathParam("list_name") String list_name,
                                  @QueryParam("column_name") String column_name) {

        File configFile = new configurationFileFetcher(projectId, uploadPath(), true).getOutputFile();

        // Create a process object
        process p = new process(
                uploadPath(),
                configFile
        );

        Validation validation = new Validation();
        p.addValidationRules(new Digester(), validation);
        digester.List results = (digester.List) validation.findList(list_name);
        // NO results mean no list has been defined!
        if (results == null) {
            return Response.ok("No list has been defined for \"" + column_name + "\" but there is a rule saying it exists.  " +
                    "Please talk to your FIMS data manager to fix this").build();
        }
        Iterator it = results.getFields().iterator();
        StringBuilder sb = new StringBuilder();

        if (column_name != null && !column_name.trim().equals("")) {
            try {
                sb.append("<b>Acceptable values for " + URLDecoder.decode(column_name, "utf-8") + "</b><br>\n");
            } catch (UnsupportedEncodingException e) {
                throw new FIMSRuntimeException(500, e);
            }
        } else {
            sb.append("<b>Acceptable values for " + list_name + "</b><br>\n");
        }

        // Get field values
        while (it.hasNext()) {
            Field f = (Field)it.next();
            sb.append("<li>" + f.getValue() + "</li>\n");
        }

        return Response.ok(sb.toString()).build();
    }

    @GET
    @Path("/isNMNHProject/{project_id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response isNMNHProject(@PathParam("project_id") Integer projectId) {
        processController processController = new processController(projectId, null);
        process p = new process(
                null,
                uploadPath(),
                null,
                processController);

        return Response.ok("{\"isNMNHProject\": \"" + p.isNMNHProject() + "\"}").build();
    }

    @GET
    @Path("/listProjects")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listProjects(@Context HttpServletRequest request) {
        HttpSession session = request.getSession();
        String accessToken = (String) session.getAttribute("access_token");
        String refreshToken = (String) session.getAttribute("refresh_token");
        bcidConnector bcidConnector = new bcidConnector(accessToken, refreshToken);

        String response = bcidConnector.fetchProjects();

        return Response.status(bcidConnector.getResponseCode())
                .entity(response)
                .build();
    }

    static String uploadpath() {
        return context.getRealPath("tripleOutput") + File.separator;
    }
}

