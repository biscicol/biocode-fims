<%@ include file="header.jsp" %>

<div class="section">

    <div class="sectioncontent">
        <h2>Uploader</h2>

        <table>
            <form method="POST">
                <tr>
                    <td align="right">Dataset</td>
                    <td><input type="file" name="dataset" id="dataset" /></td>
                </tr>

                <tr class="toggle-content" id="projects_toggle">
                    <td align="right">Project</td>
                    <td>
                        <select width=20 name="project_id" id="projects">
                            <option qdup=1 value=0>Select an project ...</option>
                            <option data-qrepeat="e projects" data-qattr="value e.project_id; text e.project_title">
                                Loading Projects ...
                            </option>
                        </select>
                    </td>
                </tr>

                <tr>
                    <td align="right">Upload</td>
                    <td style="font-size:11px;">
                        <c:if test="${user == null}">
                            <input type="checkbox" id="upload" disabled="disabled" /> (login to upload)
                        </c:if>
                        <c:if test="${user != null}">
                            <input type="checkbox" id="upload" name="upload" />
                        </c:if>
                    </td>
                </tr>

                <tr class="toggle-content" id="expedition_code_toggle">
                    <td align="right">Expedition Code</td>
                    <td><input type="text" name="expedition_code" id="expedition_code" /></td>
                </tr>

                <tr>
                    <td></td>
                    <td><input type="button" value="Submit"</td>
                </tr>
            </form>
        </table>
    </div>

    <div id="uploaderResults" class="sectioncontent-results"></div>
</div>

<script>
    $(document).ready(function() {
        uploader();
        populateProjects();
        $("input[type=button]").click(function() {
            validatorSubmit();
        });
    });
</script>

<%@ include file="footer.jsp" %>