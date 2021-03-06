package com.energyxxer.trident.ui.editor.completion.paths;

import com.energyxxer.enxlex.suggestions.Suggestion;
import com.energyxxer.trident.ui.editor.completion.SuggestionDialog;

import java.util.ArrayList;
import java.util.Collection;

public class ResourcePathExpander {

    public static final String DELIMITERS = ":/.";
    public static final String DELIMITER_REGEX = "(?<=[" + DELIMITERS + "])";

    public static Collection<ResourcePathNode> expand(Collection<String> paths, SuggestionDialog parent, Suggestion suggestion, boolean flattenResults, boolean skipNamespaces) {
        ResourcePathNode root = new ResourcePathNode();
        for(String rawPath : paths) {
            root.insert(rawPath);
        }
        if(flattenResults) {
            root.flattenAll();
        } else {
            root.flattenEmptyMiddleNodes();
            root.updatePathParts();
        }
        ArrayList<ResourcePathNode> collected = new ArrayList<>();
        root.collectSuggestionTokens(collected, parent, suggestion);
        if(skipNamespaces) {
            collected.forEach(n -> n.setSkipNamespaces(true));
        }
        return collected;
    }


    /**
     * ResourcePathExpander should not be instantiated.
     * */
    private ResourcePathExpander() {
    }
}
