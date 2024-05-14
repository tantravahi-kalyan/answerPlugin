package answerplugin.handlers;



import java.io.BufferedReader;

import java.io.IOException;

import java.io.InputStreamReader;

import java.net.HttpURLConnection;

import java.net.URL;

import java.util.ArrayList;

import java.util.HashMap;

import java.util.List;

import java.util.Map;

import java.util.regex.Matcher;

import java.util.regex.Pattern;

import org.eclipse.core.commands.AbstractHandler;

import org.eclipse.core.commands.ExecutionEvent;

import org.eclipse.core.commands.ExecutionException;

import org.eclipse.jface.dialogs.MessageDialog;

import org.eclipse.jface.text.BadLocationException;

import org.eclipse.jface.text.IDocument;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.jface.viewers.ISelection;

import org.eclipse.ui.IEditorPart;

import org.eclipse.ui.IWorkbenchWindow;

import org.eclipse.ui.handlers.HandlerUtil;

import org.eclipse.ui.texteditor.IDocumentProvider;

import org.eclipse.ui.texteditor.ITextEditor;

//import org.json.JSONException;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
//import com.fasterxml.jackson.databind.JsonNode;
//
//import com.fasterxml.jackson.databind.ObjectMapper;



public class Handler extends AbstractHandler {

    private final Map<String, String> answerCache = new HashMap<>();

    private static final String OLLAMA_API_URL = "http://localhost:11434/api/generate";

    private static final String MODEL_NAME = "codellama";



    @Override

    public Object execute(ExecutionEvent event) throws ExecutionException {

        IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);

        if (window != null) {

            IEditorPart editor = window.getActivePage().getActiveEditor();

            if (editor instanceof ITextEditor) {

                ITextEditor textEditor = (ITextEditor) editor;

                IDocumentProvider provider = textEditor.getDocumentProvider();

                IDocument document = provider.getDocument(editor.getEditorInput());



                ISelection selection = textEditor.getSelectionProvider().getSelection();

                if (selection instanceof ITextSelection) {

                    ITextSelection textSelection = (ITextSelection) selection;

                    int offset = textSelection.getOffset() + textSelection.getLength();

                    String selectedText = textSelection.getText();

                    if (selectedText.startsWith("//")) {

                        selectedText = selectedText.substring(2).trim();

                        try {

                            String answer = getOrGenerateAnswer(selectedText);

                            document.replace(offset, 0, "\n" + answer);

                        } catch (IOException  | BadLocationException e) {

                            e.printStackTrace();

                            MessageDialog.openError(

                                    window.getShell(),

                                    "Error",

                                    "An error occurred while fetching or inserting the answer.");

                        }

                    } else {

                        MessageDialog.openInformation(

                                window.getShell(),

                                "CodeLama AnswerPlugin",

                                "Please select a comment line.");

                    }

                }

            }

        }

        return null;

    }

    private String getOrGenerateAnswer(String prompt) throws IOException {

        if (answerCache.containsKey(prompt)) {

            return answerCache.get(prompt);

        } else {

            String answer = generateAnswer(prompt);

            answerCache.put(prompt, answer);

            return answer;

        }

    }



    private String generateAnswer(String prompt) throws IOException {

    	URL url = new URL(OLLAMA_API_URL);

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("POST");

        connection.setRequestProperty("Content-Type", "application/json");

        connection.setDoOutput(true);

        String postData = String.format("{\"model\": \"%s\", \"prompt\": \"%s\"}", MODEL_NAME, prompt);

        connection.getOutputStream().write(postData.getBytes());

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {

            StringBuilder response = new StringBuilder();

            String line;

            while ((line = reader.readLine()) != null) {

                response.append(line);

            }

            // Split the concatenated JSON string into individual JSON objects

            String[] jsonObjects = response.toString().split("\\}\\{");

            List<String> individualResponses = new ArrayList<>();

            for (String jsonObject : jsonObjects) {

                // Reconstruct each JSON object (add missing braces)

                if (!jsonObject.startsWith("{")) {

                    jsonObject = "{" + jsonObject;

                }

                if (!jsonObject.endsWith("}")) {

                    jsonObject += "}";

                }

                individualResponses.add(jsonObject);

            }

            // Parse each JSON object to extract the "response" field

            JsonParser parser = new JsonParser();
            StringBuilder responseBuilder = new StringBuilder();
            for (String jsonResponse : individualResponses) {
                JsonElement element = parser.parse(jsonResponse);
                JsonObject jsonObject = element.getAsJsonObject();
                JsonElement responseElement = jsonObject.get("response");
                if (responseElement != null && responseElement.isJsonPrimitive()) {
                    responseBuilder.append(responseElement.getAsString()).append(" ");
                }
            }

  String output = responseBuilder.toString().trim(); // Trim to remove any leading or trailing whitespace



            // Extract Maven dependency code using regex

        String dependencyCode = extractDependencyCode(output);

            dependencyCode = dependencyCode.replace(" ", "");

            //return dependencyCode;

            String[] lines = dependencyCode.split("\n");

            StringBuilder indentedOutput = new StringBuilder();

            indentedOutput.append(lines[0]).append("\n"); // Append first line without indentation

            for (int i = 1; i < lines.length - 1; i++) {

                indentedOutput.append("\t").append(lines[i]).append("\n");

            }

            indentedOutput.append(lines[lines.length - 1]); // Append last line without indentation

            return indentedOutput.toString();

        } finally {

            connection.disconnect();

        }



    }

    



    private String extractDependencyCode(String response) {

        String dependencyCode = "";

        Pattern pattern = Pattern.compile("<dependency >.*?</ dependency >", Pattern.DOTALL);

        Matcher matcher = pattern.matcher(response);

        if (matcher.find()) {

            dependencyCode = matcher.group(0);

        }

        return dependencyCode;

    }



}