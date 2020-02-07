package hudson.plugins.brakeman.scanners;

import hudson.plugins.analysis.core.ParserResult;
import hudson.plugins.analysis.util.PluginLogger;
import hudson.plugins.analysis.util.model.Priority;
import hudson.plugins.brakeman.Warning;

import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.apache.commons.lang3.StringUtils;

/**
 * A Java class that represents a JSON Scanner of the Brakeman Output file
 * This class includes a specific strategy for implementing the parsing process
 * of the output file.
 *
 * @author Thomas Baird
 */
public class BrakemanJSONScanner extends AbstractBrakemanScanner {

    /** Enum for possible warning/annotation categories */
    public enum Category {
        GENERAL, IGNORED;

        public String getName() {
           return StringUtils.capitalize(name().toLowerCase());
        }
    }

    /**
     * Creates a new instance of <code>BrakemanJSONScanner</code>
     */
    public BrakemanJSONScanner() {}

    public boolean scan(String content, ParserResult project, PluginLogger logger) {
        boolean result = true;
        try {
            JSONObject brakemanFile = JSONObject.fromObject(content);
            scanJSONResultSet(brakemanFile, project, "warnings");
            scanJSONResultSet(brakemanFile, project, "ignored_warnings");
        } catch (JSONException e) {
            result = false;
            logger.log(String.format("%s", e.getMessage()));
        }
        return result;
    }

    private void scanJSONResultSet(JSONObject brakemanResultFile, ParserResult project, String filterType) throws JSONException {
        JSONArray rows = brakemanResultFile.getJSONArray(filterType);
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            String fileName = escapeHTML(row.getString("file"));
            String type = escapeHTML(row.getString("warning_type"));
            String category = getCategory(filterType).getName();
            StringBuilder message = new StringBuilder();
            message.append(row.getString("message"));

            if(row.has("code")) {
              String code = row.optString("code", "");

              if(!code.isEmpty()) {
                message.
                  append(": ").
                  append(row.getString("code"));
              }
            }

            int line = row.optInt("line", 1);

            Priority priority = checkPriority(row.getString("confidence"));

            String escapedMessage = escapeHTML(message.toString());

            if(row.has("description")) { // Brakeman Pro reports have extended descriptions
              String description = escapeHTML(row.getString("description"));

              project.addAnnotation(new Warning(fileName, getStart(line), getEnd(line), type, category, escapedMessage, description, priority));
            } else {
              project.addAnnotation(new Warning(fileName, getStart(line), getEnd(line), type, category, escapedMessage, priority));
            }
        }
    }

    private Category getCategory(String filterType) {
        Category category;
        if (filterType == "ignored_warnings") {
            category = Category.IGNORED;
        } else {
            category = Category.GENERAL;
        }
        return category;
    }
}
